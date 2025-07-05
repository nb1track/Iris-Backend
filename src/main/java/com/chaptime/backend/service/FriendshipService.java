package com.chaptime.backend.service;

import com.chaptime.backend.dto.FriendRequestDTO;
import com.chaptime.backend.dto.FriendshipActionDTO;
import com.chaptime.backend.dto.UserDTO;
import com.chaptime.backend.model.Friendship;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.FriendshipStatus;
import com.chaptime.backend.repository.FriendshipRepository;
import com.chaptime.backend.repository.UserRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    private static final double MAX_DISTANCE_METERS = 50.0; // Max. 50 Meter Entfernung


    public FriendshipService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

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

    // Gibt eine Liste von User-Objekten zurück, was wir für den Feed brauchen
    public List<User> getFriendsAsEntities(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> friendship.getUserOne().getId().equals(userId) ? friendship.getUserTwo() : friendship.getUserOne())
                .collect(Collectors.toList());
    }

    public void sendFriendRequest(FriendRequestDTO request) {
        User requester = userRepository.findById(request.requesterId())
                .orElseThrow(() -> new RuntimeException("Requester not found"));

        User addressee = userRepository.findById(request.addresseeId())
                .orElseThrow(() -> new RuntimeException("Addressee not found"));

        // Standort-Check
        Point requesterLocation = requester.getLastLocation();
        Point addresseeLocation = addressee.getLastLocation();

        if (requesterLocation == null || addresseeLocation == null) {
            throw new IllegalStateException("User location not available.");
        }

        // Zeit-Check
        long requesterLocAge = Duration.between(requester.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes();
        long addresseeLocAge = Duration.between(addressee.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes();
        if (requesterLocAge > 5 || addresseeLocAge > 5) {
            throw new IllegalStateException("User location is outdated.");
        }

        // Distanz-Check
        double distance = requesterLocation.distance(addresseeLocation);
        if (distance > MAX_DISTANCE_METERS) {
            throw new SecurityException("Users are not close enough to send a friend request.");
        }

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

    public void acceptFriendRequest(FriendshipActionDTO request) {
        Friendship friendship = friendshipRepository.findById(request.friendshipId())
                .orElseThrow(() -> new RuntimeException("Friendship not found"));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Request is not pending anymore.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }
}