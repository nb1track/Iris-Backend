package com.iris.backend.service;

import com.iris.backend.dto.FriendAtSpotDTO;
import com.iris.backend.dto.FriendshipActionDTO;
import com.iris.backend.dto.PendingRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.dto.LocationReportDTO;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.repository.UserRepository;
import com.iris.backend.service.GalleryFeedService;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FriendshipService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService;
    private final FcmService fcmService;
    private final GooglePlaceRepository googlePlaceRepository;
    private final CustomPlaceRepository customPlaceRepository;
    private final GalleryFeedService galleryFeedService;

    @Value("${gcs.bucket.profile-images.name}")
    private String profileImagesBucketName;
    private static final Logger logger = LoggerFactory.getLogger(FriendshipService.class);

    private static final double MAX_DISTANCE_METERS = 50.0;

    /**
     * Konstruktor aktualisiert mit GcsStorageService
     */
    public FriendshipService(UserRepository userRepository,
                             FriendshipRepository friendshipRepository,
                             GcsStorageService gcsStorageService,
                             FcmService fcmService,
                             GooglePlaceRepository googlePlaceRepository,
                             CustomPlaceRepository customPlaceRepository,
                             GalleryFeedService galleryFeedService) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService;
        this.fcmService = fcmService;
        this.googlePlaceRepository = googlePlaceRepository;
        this.customPlaceRepository = customPlaceRepository;
        this.galleryFeedService = galleryFeedService;
    }

    /**
     * Findet alle Spots (Google & Iris), an denen sich Freunde des aktuellen Benutzers
     * gerade aufhalten (Standort-Update innerhalb der letzten 5 Minuten).
     *
     * @param currentUser Der eingeloggte Benutzer.
     * @return Eine Liste von Spots, angereichert mit den Freunden, die dort sind.
     */
    @Transactional(readOnly = true)
    public List<FriendAtSpotDTO> getFriendsAtSpots(User currentUser) {
        OffsetDateTime fiveMinutesAgo = OffsetDateTime.now().minusMinutes(5);

        // 1. Finde alle Freunde mit einem aktuellen Standort-Update
        List<User> activeFriends = getFriendsAsEntities(currentUser.getId()).stream()
                .filter(friend -> friend.getLastLocation() != null &&
                        friend.getLastLocationUpdatedAt() != null &&
                        friend.getLastLocationUpdatedAt().isAfter(fiveMinutesAgo))
                .toList();

        if (activeFriends.isEmpty()) {
            return List.of(); // Keine aktiven Freunde, keine Spots
        }

        // 2. Erstelle eine Map, um Freunde pro Spot zu sammeln
        // Wir brauchen einen eindeutigen Key für Google (Long) und Custom (UUID)
        // Wir nutzen "g-123" für GooglePlace 123 und "c-abc..." für CustomPlace abc...
        Map<String, List<User>> friendsPerSpotKey = new java.util.HashMap<>();
        Map<String, Object> spotEntityMap = new java.util.HashMap<>();

        // 3. Iteriere durch jeden aktiven Freund und finde seine Spots
        for (User friend : activeFriends) {
            double lat = friend.getLastLocation().getY();
            double lon = friend.getLastLocation().getX();

            // Finde Google Places für den Freund
            List<GooglePlace> googlePlaces = googlePlaceRepository.findActivePlacesForUserLocation(lat, lon);
            for (GooglePlace spot : googlePlaces) {
                String key = "g-" + spot.getId();
                spotEntityMap.putIfAbsent(key, spot);
                friendsPerSpotKey.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(friend);
            }

            // Finde Custom Places für den Freund
            List<CustomPlace> customPlaces = customPlaceRepository.findActivePlacesForUserLocation(lat, lon);
            for (CustomPlace spot : customPlaces) {
                String key = "c-" + spot.getId();
                spotEntityMap.putIfAbsent(key, spot);
                friendsPerSpotKey.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(friend);
            }
        }

        // 4. Konvertiere die gesammelten Daten in die finalen DTOs
        return friendsPerSpotKey.entrySet().stream()
                .map(entry -> {
                    String spotKey = entry.getKey();
                    Object spotEntity = spotEntityMap.get(spotKey);

                    // Konvertiere das Entity (Google/Custom) in ein GalleryFeedItemDTO
                    GalleryFeedItemDTO spotInfo;
                    if (spotEntity instanceof GooglePlace gp) {
                        // 'true' = Lade Foto-Infos (Coverbild etc.)
                        spotInfo = galleryFeedService.getFeedItemForPlace(gp, true);
                    } else if (spotEntity instanceof CustomPlace cp) {
                        spotInfo = galleryFeedService.getFeedItemForPlace(cp, true);
                    } else {
                        return null; // Sollte nicht passieren
                    }

                    // Konvertiere die Liste der User-Entities in UserDTOs (mit Profilbild-URL)
                    List<UserDTO> friendDTOs = entry.getValue().stream()
                            .map(this::toUserDTOWithSignedUrl) // Diese Helfermethode haben wir schon
                            .toList();

                    return new FriendAtSpotDTO(spotInfo, friendDTOs);
                })
                .filter(dto -> dto != null && !dto.friendsAtSpot().isEmpty())
                .sorted(Comparator.comparing(dto -> dto.spotInfo().name())) // Sortiere nach Spot-Name
                .toList();
    }

    /**
     * Retrieves the list of friends for a specified user as Data Transfer Object (DTO) representations.
     *
     * (Methode aktualisiert, um signierte Profilbild-URLs zu generieren)
     */
    public List<UserDTO> getFriendsAsDTO(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> {
                    User friend = friendship.getUserOne().getId().equals(userId) ? friendship.getUserTwo() : friendship.getUserOne();

                    // --- NEUE LOGIK (Signierte URL) ---
                    String signedProfileUrl = null;
                    String objectName = friend.getProfileImageUrl();

                    if (objectName != null && !objectName.isBlank()) {
                        signedProfileUrl = gcsStorageService.generateSignedUrl(
                                profileImagesBucketName,
                                objectName,
                                15,
                                TimeUnit.MINUTES
                        );
                    }
                    // --- ENDE NEUE LOGIK ---

                    // Konstruktor mit 3 Argumenten
                    return new UserDTO(friend.getId(), friend.getUsername(), signedProfileUrl);
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the list of friends for a specified user as entity representations.
     * (Diese Methode bleibt unverändert, da sie keine DTOs verwendet)
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
        User addressee = userRepository.findById(addresseeId)
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

        User userOne = requester.getId().compareTo(addressee.getId()) < 0 ? requester : addressee;
        User userTwo = requester.getId().compareTo(addressee.getId()) < 0 ? addressee : requester;

        // Prüfe, ob bereits eine Beziehung existiert
        if (friendshipRepository.existsByUserOneAndUserTwo(userOne, userTwo)) {
            throw new IllegalStateException("A friendship or pending request already exists between these users.");
        }

        // Neue Freundschaftsanfrage erstellen
        Friendship newFriendship = new Friendship();
        newFriendship.setUserOne(userOne);
        newFriendship.setUserTwo(userTwo);
        newFriendship.setStatus(FriendshipStatus.PENDING);
        newFriendship.setActionUser(requester);

        friendshipRepository.save(newFriendship);
    }

    /**
     * NEU: Startet die "Aktiv-System"-Anfrage.
     * Sendet Push-Nachrichten an alle Freunde des aktuellen Benutzers.
     */
    @Transactional(readOnly = true)
    public void requestFriendLocationRefresh(User currentUser) {
        String requesterFcmToken = currentUser.getFcmToken();
        if (requesterFcmToken == null || requesterFcmToken.isBlank()) {
            throw new IllegalStateException("Benutzer hat kein FCM-Token, um Antworten zu empfangen.");
        }

        // 1. Finde alle Freunde
        List<User> friends = getFriendsAsEntities(currentUser.getId());

        // 2. Sammle deren FCM-Tokens
        List<String> friendTokens = friends.stream()
                .map(User::getFcmToken)
                .filter(token -> token != null && !token.isBlank())
                .collect(Collectors.toList());

        if (friendTokens.isEmpty()) {
            logger.info("Keine Freunde mit FCM-Tokens gefunden für User {}.", currentUser.getUsername());
            return;
        }

        // 3. Starte den FCM-Service, um die Anfragen zu senden
        fcmService.sendLocationRefreshRequest(friendTokens, requesterFcmToken);
    }

    /**
     * NEU: Verarbeitet die "Aktiv-System"-Antwort.
     * Wird von einem Freund (B) aufgerufen, um seinen Standort an den Anfrager (A) zu senden.
     */
    public void reportLocationToRequester(User friend, LocationReportDTO report) {
        // Der 'friend' (User B) wird aus dem @AuthenticationPrincipal geholt.
        // Das 'report' (enthält User A's Token) kommt aus dem Request Body.

        // 1. (Optional) Aktualisiere den Standort von User B in der DB (gute Synergie)
        // Wir können dies hier tun, anstatt auf den 5-Minuten-Poll zu warten.
        // HINWEIS: Dies erfordert, dass die Methode @Transactional ist.
        // (Für die erste Implementierung lassen wir es einfach und fokussieren uns auf FCM)

        // 2. Sende die Antwort-Push an User A
        fcmService.sendLocationRefreshResponse(
                report.targetFcmToken(),
                friend,
                report.latitude(),
                report.longitude()
        );
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
    /**
     * Retrieves a list of pending friend requests for the specified user.
     * The DTO now includes the sender's profile picture URL.
     *
     * @param currentUser The user for whom pending requests are to be retrieved.
     * @return A list of PendingRequestDTO objects.
     */
    @Transactional(readOnly = true)
    public List<PendingRequestDTO> getPendingRequests(User currentUser) {
        // Die Logik zum Holen der Anfragen bleibt gleich
        List<Friendship> requestsAsUserOne = friendshipRepository
                .findByUserOneAndStatusAndActionUserNot(currentUser, FriendshipStatus.PENDING, currentUser);

        List<Friendship> requestsAsUserTwo = friendshipRepository
                .findByUserTwoAndStatusAndActionUserNot(currentUser, FriendshipStatus.PENDING, currentUser);

        // --- HIER IST DIE WICHTIGE ÄNDERUNG ---
        // Wir führen beide Listen zusammen und wandeln sie in die neuen DTOs um.
        return Stream.concat(requestsAsUserOne.stream(), requestsAsUserTwo.stream())
                .map(friendship -> {
                    User sender = friendship.getActionUser();
                    String signedProfileUrl = null;

                    // Nur eine URL generieren, wenn der Absender ein Profilbild hat
                    if (sender.getProfileImageUrl() != null && !sender.getProfileImageUrl().isBlank()) {
                        // Rufe die flexible Methode im GCS-Service auf
                        signedProfileUrl = gcsStorageService.generateSignedUrl(
                                this.profileImagesBucketName, // 1. Bucket-Name für Profilbilder
                                sender.getProfileImageUrl(),  // 2. Der Objektname (z.B. die UID.jpg)
                                15,                           // 3. Gültigkeitsdauer
                                TimeUnit.MINUTES              // 4. Zeiteinheit
                        );
                    }

                    // Erstelle das DTO mit allen drei Informationen
                    return new PendingRequestDTO(
                            friendship.getId(),
                            sender.getUsername(),
                            signedProfileUrl
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Finds the nearby friends of the current user at a specific place.
     * This method retrieves the friends of the given user, checks if they are within
     * a small radius of the provided location, and filters them based on their last location update.
     *
     * @param currentUser The user for whom nearby friends are being searched.
     * @param placeLocation The location (represented as a point) to search for nearby friends.
     * @return A list of UserDTOs representing the user's nearby friends at the specified location within the defined radius.
     *         Returns an empty list if no friends are nearby or available.
     */
    /**
     * Findet Freunde in der Nähe eines Ortes und gibt sie als DTOs zurück,
     * inklusive einer signierten URL für ihr Profilbild.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> findNearbyFriendsAtPlace(User currentUser, Point placeLocation) {
        if (placeLocation == null) {
            return List.of();
        }

        List<UUID> friendIds = getFriendsAsEntities(currentUser.getId()).stream()
                .map(User::getId)
                .collect(Collectors.toList());

        if (friendIds.isEmpty()) {
            return List.of();
        }

        double radius = 100.0; // 100 Meter Radius
        List<User> nearbyFriends = userRepository.findFriendsByIdsAndLocation(friendIds, placeLocation, radius);

        return nearbyFriends.stream()
                .filter(friend -> friend.getLastLocationUpdatedAt() != null &&
                        Duration.between(friend.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes() <= 5)
                .map(this::toUserDTOWithSignedUrl)
                .collect(Collectors.toList());
    }

    /**
     * Converts a User entity to a UserDTO, generating a signed URL for the profile picture if available.
     *
     * @param user The User entity to convert.
     * @return A UserDTO with the user's ID, username, and a signed URL for the profile picture.
     */
    private UserDTO toUserDTOWithSignedUrl(User user) {
        String signedProfileUrl = null;
        String objectName = user.getProfileImageUrl();

        if (objectName != null && !objectName.isBlank()) {
            // KORRIGIERTER AUFRUF: Jetzt mit allen vier Parametern
            signedProfileUrl = gcsStorageService.generateSignedUrl(
                    this.profileImagesBucketName, // 1. Bucket-Name
                    objectName,                   // 2. Objekt-Name
                    15,                           // 3. Dauer
                    TimeUnit.MINUTES              // 4. Zeiteinheit
            );
        }

        return new UserDTO(user.getId(), user.getUsername(), signedProfileUrl);
    }

    /**
     * Rejects a friend request by deleting the friendship entry from the database.
     *
     * A security check ensures that only a user involved in the friendship
     * (specifically the one who did NOT send the request) can reject it.
     *
     * @param friendshipId The ID of the friendship request to be rejected.
     * @param currentUser The user attempting to reject the request.
     * @throws RuntimeException if the friendship is not found.
     * @throws SecurityException if the user is not authorized to reject the request.
     */
    @Transactional
    public void rejectFriendRequest(UUID friendshipId, User currentUser) {
        // 1. Finde die Freundschaftsanfrage in der Datenbank
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friendship request not found with ID: " + friendshipId));

        // 2. Sicherheits-Check: Darf der aktuelle Benutzer diese Anfrage ablehnen?
        // Nur der Empfänger der Anfrage darf sie ablehnen. Der Empfänger ist derjenige,
        // der NICHT der 'actionUser' (der Absender) ist.
        if (friendship.getActionUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You cannot reject a friend request you sent yourself.");
        }
        // Zusätzlicher Check: Ist der User überhaupt Teil dieser Freundschaft?
        if (!friendship.getUserOne().getId().equals(currentUser.getId()) && !friendship.getUserTwo().getId().equals(currentUser.getId())) {
            throw new SecurityException("You are not part of this friendship request.");
        }

        // 3. Wenn alle Checks bestanden sind, lösche den Eintrag komplett.
        friendshipRepository.deleteById(friendshipId);
    }

    /**
     * Removes a friendship between the current user and another user.
     *
     * @param currentUser The user initiating the removal.
     * @param friendId The ID of the friend to be removed.
     * @throws RuntimeException if the friend or the friendship is not found.
     */
    @Transactional
    public void removeFriend(User currentUser, UUID friendId) {
        User friendToRemove = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Friend to remove not found with ID: " + friendId));

        Friendship friendship = friendshipRepository.findFriendshipBetweenUsers(currentUser, friendToRemove)
                .orElseThrow(() -> new RuntimeException("Friendship not found between users."));

        // Optionaler Check: Ist die Freundschaft überhaupt "ACCEPTED"?
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot remove a friendship that is not accepted.");
        }

        friendshipRepository.delete(friendship);
    }

    /**
     * Sendet einen Ping an einen Freund.
     */
    @Transactional(readOnly = true)
    public void pingFriend(User sender, UUID targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Sicherheits-Check: Sind sie Freunde?
        boolean areFriends = friendshipRepository.findFriendshipBetweenUsers(sender, target)
                .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);

        if (!areFriends) {
            throw new SecurityException("You can only ping accepted friends.");
        }

        // 2. Check FCM Token
        String targetFcmToken = target.getFcmToken();
        if (targetFcmToken == null || targetFcmToken.isBlank()) {
            throw new IllegalStateException("Friend is not reachable (no FCM token).");
        }

        // 3. Generiere signierte URL für das Sender-Profilbild
        String senderProfileUrl = null;
        if (sender.getProfileImageUrl() != null) {
            senderProfileUrl = gcsStorageService.generateSignedUrl(
                    profileImagesBucketName,
                    sender.getProfileImageUrl(),
                    15,
                    TimeUnit.MINUTES
            );
        }

        // 4. Sende Nachricht
        fcmService.sendPingNotification(targetFcmToken, sender, senderProfileUrl);
    }
}