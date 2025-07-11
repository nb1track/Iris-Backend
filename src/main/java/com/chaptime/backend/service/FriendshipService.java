package com.chaptime.backend.service;

import com.chaptime.backend.dto.FriendshipActionDTO;
import com.chaptime.backend.dto.PendingRequestDTO;
import com.chaptime.backend.dto.UserDTO;
import com.chaptime.backend.model.Friendship;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.FriendshipStatus;
import com.chaptime.backend.repository.FriendshipRepository;
import com.chaptime.backend.repository.UserRepository;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FriendshipService {

    private static final Logger logger = LoggerFactory.getLogger(FriendshipService.class);

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private static final double MAX_DISTANCE_METERS = 50.0; // Max. 50 Meter Entfernung

    /**
     * Constructs a new instance of the FriendshipService.
     *
     * @param userRepository the repository used for accessing and managing User entities
     * @param friendshipRepository the repository used for accessing and managing Friendship entities
     */
    public FriendshipService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    /**
     * Retrieves the list of friends for a specified user as Data Transfer Object (DTO) representations.
     *
     * This method fetches all friendships where the given user is either User One or User Two
     * and the friendship status is 'ACCEPTED'. It then transforms the associated user entities
     * into DTOs containing their id and username.
     *
     * @param userId the unique identifier of the user for whom the friend list is to be retrieved
     * @return a list of UserDTO objects representing the user's friends
     * @throws RuntimeException if the user with the specified ID is not found
     */
    public List<UserDTO> getFriendsAsDTO(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> {
                    User friend = friendship.getUserOne().getId().equals(userId) ? friendship.getUserTwo() : friendship.getUserOne();
                    return new UserDTO(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the list of friends for a specified user as entity representations.
     *
     * This method retrieves all friendships where the given user is either User One or User Two
     * and the friendship status is 'ACCEPTED'. It then returns the associated user entities
     * representing the user's friends.
     *
     * @param userId the unique identifier of the user for whom the friend list is to be retrieved
     * @return a list of User entities representing the user's friends
     * @throws RuntimeException if the user with the specified ID is not found
     */
    public List<User> getFriendsAsEntities(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> friendship.getUserOne().getId().equals(userId) ? friendship.getUserTwo() : friendship.getUserOne())
                .collect(Collectors.toList());
    }

    /**
     * Sends a friend request from one user to another.
     *
     * This method verifies the geographical proximity of the two users,
     * the freshness of their location data, and other criteria before
     * allowing a friend request to be sent. If all conditions are met,
     * a new friendship with a "PENDING" status is created.
     *
     * @param requester the User entity sending the friend request
     * @param addresseeId the unique identifier of the User receiving the friend request
     * @throws RuntimeException if the addressee is not found
     * @throws IllegalStateException if the location of either user is unavailable or outdated
     * @throws SecurityException if the users are not within the maximum allowed distance
     */
    public void sendFriendRequest(User requester, UUID addresseeId) {
        logger.info("--- [sendFriendRequest] Starting friend request from {} to {}", requester.getUsername(), addresseeId);

        User addressee = userRepository.findById(addresseeId)
                .orElseThrow(() -> new RuntimeException("Addressee not found"));
        logger.info("--- [sendFriendRequest] Addressee {} found.", addressee.getUsername());

        // Standort-Check
        Point requesterLocation = requester.getLastLocation();
        Point addresseeLocation = addressee.getLastLocation();

        if (requesterLocation == null || addresseeLocation == null) {
            logger.error("--- [sendFriendRequest] Location not available for one or both users.");
            throw new IllegalStateException("User location not available.");
        }
        logger.info("--- [sendFriendRequest] Locations are available for both users.");

        // Zeit-Check
        long requesterLocAge = Duration.between(requester.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes();
        long addresseeLocAge = Duration.between(addressee.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes();
        logger.info("--- [sendFriendRequest] Location age check: Requester {} mins, Addressee {} mins.", requesterLocAge, addresseeLocAge);

        if (requesterLocAge > 5 || addresseeLocAge > 5) {
            logger.warn("--- [sendFriendRequest] Location is outdated for one or both users.");
            throw new IllegalStateException("User location is outdated.");
        }

        // Distanz-Check
        double distance = requesterLocation.distance(addresseeLocation);
        logger.info("--- [sendFriendRequest] Calculated distance is {} meters.", distance);

        if (distance > MAX_DISTANCE_METERS) {
            logger.warn("--- [sendFriendRequest] Distance check FAILED. Distance {} is greater than max {}.", distance, MAX_DISTANCE_METERS);
            throw new SecurityException("Users are not close enough to send a friend request.");
        }
        logger.info("--- [sendFriendRequest] Distance check PASSED.");

        // Neue Freundschaftsanfrage erstellen
        Friendship newFriendship = new Friendship();
        if (requester.getId().compareTo(addressee.getId()) < 0) {
            newFriendship.setUserOne(requester);
            newFriendship.setUserTwo(addressee);
        } else {
            newFriendship.setUserOne(addressee);
            newFriendship.setUserTwo(requester);
        }
        newFriendship.setStatus(FriendshipStatus.PENDING);
        newFriendship.setActionUser(requester);

        friendshipRepository.save(newFriendship);
    }

    /**
     * Processes the acceptance of a friend request.
     *
     * This method retrieves the specified friendship and updates its status
     * to "ACCEPTED" if it was previously pending. It ensures that the user
     * accepting the request is the recipient of the original request and not
     * the sender. If the request is invalid or violates security rules, an
     * exception is thrown.
     *
     * @param request an instance of FriendshipActionDTO containing the friendship ID
     *                for which the acceptance action is to be performed
     * @param acceptor the User entity representing the individual accepting the friend request
     * @throws RuntimeException if the friendship with the provided ID is not found
     * @throws IllegalStateException if the friendship status is not "PENDING"
     * @throws SecurityException if the user attempting to accept is the sender of the friend request
     */
    public void acceptFriendRequest(FriendshipActionDTO request, User acceptor) {
        Friendship friendship = friendshipRepository.findById(request.friendshipId())
                .orElseThrow(() -> new RuntimeException("Friendship not found"));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Request is not pending anymore.");
        }

        // SICHERHEITS-CHECK: Ist der annehmende User auch der Empfänger der Anfrage?
        // (Der Empfänger ist der, der die Anfrage NICHT gesendet hat)
        if (friendship.getActionUser().getId().equals(acceptor.getId())) {
            throw new SecurityException("You cannot accept your own friend request.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setActionUser(acceptor); // Setze den annehmenden User als letzten Akteur
        friendshipRepository.save(friendship);
    }

    /**
     * Retrieves a list of pending friend requests for the specified user. The pending
     * requests are those where the given user is not the sender of the request.
     *
     * @param currentUser The user for whom pending requests are to be retrieved.
     * @return A list of PendingRequestDTO objects representing the pending friend requests.
     */
    @Transactional(readOnly = true)
    public List<PendingRequestDTO> getPendingRequests(User currentUser) {
        // Finde alle PENDING Anfragen, bei denen der currentUser NICHT der Absender ist.
        List<Friendship> requestsAsUserOne = friendshipRepository
                .findAllByStatusAndUserOneAndActionUserNot(FriendshipStatus.PENDING, currentUser, currentUser);

        List<Friendship> requestsAsUserTwo = friendshipRepository
                .findAllByStatusAndUserTwoAndActionUserNot(FriendshipStatus.PENDING, currentUser, currentUser);

        // Kombiniere die Listen und mappe sie auf DTOs
        return Stream.concat(requestsAsUserOne.stream(), requestsAsUserTwo.stream())
                .map(friendship -> new PendingRequestDTO(
                        friendship.getId(),
                        friendship.getActionUser().getUsername()
                ))
                .collect(Collectors.toList());
    }
}