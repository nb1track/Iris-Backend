package com.iris.backend.service;

import com.iris.backend.dto.*;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Logger logger = LoggerFactory.getLogger(UserService.class); // NEU
    private final String photosBucketName;
    private final String profileImagesBucketName;

    public UserService(
            UserRepository userRepository,
            PhotoRepository photoRepository,
            FriendshipRepository friendshipRepository,
            GcsStorageService gcsStorageService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService;
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    @Transactional
    public void updateUserLocation(UUID userId, LocationUpdateRequestDTO locationUpdate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        Point newLocation = geometryFactory.createPoint(new Coordinate(locationUpdate.longitude(), locationUpdate.latitude()));

        user.setLastLocation(newLocation);
        user.setLastLocationUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDataExportDTO exportUserData(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Photo> photos = photoRepository.findAllByUploader(user);
        List<ExportedPhotoDTO> photoDTOs = photos.stream()
                .map(p -> new ExportedPhotoDTO(
                        p.getId(),
                        p.getStorageUrl(),
                        p.getVisibility(),
                        p.getUploadedAt(),
                        p.getLocation().getY(),
                        p.getLocation().getX()
                ))
                .collect(Collectors.toList());

        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);
        List<ExportedFriendshipDTO> friendDTOs = friendships.stream()
                .map(f -> {
                    User friend = f.getUserOne().getId().equals(userId) ? f.getUserTwo() : f.getUserOne();
                    return new ExportedFriendshipDTO(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());

        return new UserDataExportDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                photoDTOs,
                friendDTOs
        );
    }

    @Transactional
    public void deleteUserAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Photo> photosToDelete = photoRepository.findAllByUploader(user);
        for (Photo photo : photosToDelete) {
            // KORRIGIERT: Bucket-Name wird jetzt mitgegeben
            gcsStorageService.deleteFile(photosBucketName, photo.getStorageUrl());
        }

        // NEU: Auch das Profilbild aus GCS löschen
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) {
            gcsStorageService.deleteFile(profileImagesBucketName, user.getProfileImageUrl());
        }
        userRepository.delete(user);
    }

    public User registerNewUser(FirebaseToken decodedToken, String username, String base64Image) {
        if (userRepository.findByFirebaseUid(decodedToken.getUid()).isPresent()) {
            logger.warn("Attempted to register an already existing user with UID: {}", decodedToken.getUid()); // NEU
            throw new IllegalStateException("User already exists in our database.");
        }
        User newUser = new User();
        newUser.setFirebaseUid(decodedToken.getUid());
        newUser.setEmail(decodedToken.getEmail());
        newUser.setUsername(username);

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Image);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid base64 image provided.");
        }

        // KORRIGIERT: Speichert nur den Objektnamen, den die neue Methode zurückgibt
        String imageUrl = gcsStorageService.uploadProfileImage(decodedToken.getUid(), imageBytes);
        newUser.setProfileImageUrl(imageUrl);

        logger.info("--> Attempting to save new user '{}' with UID {}", username, decodedToken.getUid()); // NEU
        User savedUser = userRepository.save(newUser);
        logger.info("<-- Successfully saved new user with database ID {}", savedUser.getId()); // NEU
        return savedUser;
    }

    /**
     * Finds nearby users who are not already friends, have no pending requests,
     * and have an up-to-date location. The returned DTO includes a signed URL
     * for the profile picture.
     *
     * @param latitude       The latitude of the central point.
     * @param longitude      The longitude of the central point.
     * @param radiusInMeters The search radius in meters.
     * @param currentUser    The user performing the search, to exclude them and their existing relations.
     * @return A list of UserDTOs, each containing the user's ID, username, and a temporary URL for their profile image.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getNearbyUsers(double latitude, double longitude, double radiusInMeters, User currentUser) {
        // 1. Hole alle Benutzer im Radius, außer dem aktuellen User
        List<User> usersInRadius = userRepository.findNearbyUsersByLocation(
                latitude,
                longitude,
                radiusInMeters,
                currentUser.getId() // KORRIGIERT: Ruft die neue, sichere Repository-Methode auf
        );

        // 2. Hole alle IDs von Benutzern, mit denen bereits eine Beziehung besteht (Freunde oder offen)
        Set<UUID> existingRelationsIds = friendshipRepository.findByUserOneOrUserTwo(currentUser, currentUser)
                .stream()
                .map(friendship -> friendship.getUserOne().getId().equals(currentUser.getId())
                        ? friendship.getUserTwo().getId()
                        : friendship.getUserOne().getId())
                .collect(Collectors.toSet());

        // 3. Filtere die Liste nach den gewünschten Kriterien
        List<User> filteredUsers = usersInRadius.stream()
                .filter(user -> {
                    // Bedingung A: Standort darf nicht veraltet sein (null-check + Zeit)
                    boolean isLocationRecent = user.getLastLocationUpdatedAt() != null &&
                            Duration.between(user.getLastLocationUpdatedAt(), OffsetDateTime.now()).toMinutes() <= 5;

                    // Bedingung B: Es darf keine bestehende Beziehung geben
                    boolean noExistingRelation = !existingRelationsIds.contains(user.getId());

                    return isLocationRecent && noExistingRelation;
                })
                .collect(Collectors.toList());

        // 4. Wandle das Ergebnis in DTOs um, inklusive der signierten Profilbild-URL
        return filteredUsers.stream()
                .map(user -> {
                    // KORRIGIERT: Nutzt die neue, flexible generateSignedUrl-Methode
                    String signedProfileUrl = null;
                    String objectName = user.getProfileImageUrl();

                    if (objectName != null && !objectName.isBlank()) {
                        // Generiere die temporäre, sichere URL für 15 Minuten
                        signedProfileUrl = gcsStorageService.generateSignedUrl(profileImagesBucketName, objectName, 15, TimeUnit.MINUTES);
                    }

                    // Erstelle das DTO mit der ID, dem Namen und der (eventuell null) URL
                    return new UserDTO(
                            user.getId(),
                            user.getUsername(),
                            signedProfileUrl
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts a User entity into a UserDTO for profile display.
     * It generates a fresh signed URL for the user's profile picture.
     *
     * @param currentUser The User entity to be converted.
     * @return A UserDTO containing public-facing profile information.
     */
    public UserDTO getUserProfile(User currentUser) {
        String signedProfileUrl = null;
        String objectName = currentUser.getProfileImageUrl();

        // Prüfe, ob der User überhaupt ein Profilbild hat
        if (objectName != null && !objectName.isBlank()) {
            // Generiere eine neue, temporär gültige URL
            signedProfileUrl = gcsStorageService.generateSignedUrl(
                    this.profileImagesBucketName, // Der korrekte Bucket-Name
                    objectName,                   // Der in der DB gespeicherte Dateiname
                    15,                           // Gültigkeitsdauer
                    TimeUnit.MINUTES              // Zeiteinheit
            );
        }

        // Erstelle und gib das DTO zurück
        return new UserDTO(
                currentUser.getId(),
                currentUser.getUsername(),
                signedProfileUrl // Entweder die URL oder null
        );
    }

    @Transactional
    public void updateFcmToken(User user, String token) {
        // Optional: Prüfen, ob der Token sich geändert hat, um unnötige Schreibvorgänge zu vermeiden
        if (token != null && !token.equals(user.getFcmToken())) {
            user.setFcmToken(token);
            userRepository.save(user);
        }
    }

    /**
     * Führt eine paginierte Suche nach Benutzern durch.
     *
     * @param query Der Suchbegriff.
     * @param currentUser Der eingeloggte Benutzer, der die Suche durchführt.
     * @param pageable Paginierungsinformationen.
     * @return Eine Seite (Page) von UserDTOs.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> searchUsers(String query, User currentUser, Pageable pageable) {
        // 1. Rufe die neue Repository-Methode auf
        Page<User> userPage = userRepository.searchUsers(query, currentUser.getId(), pageable);

        // 2. Wandle jede Seite von User-Objekten in UserDTOs um
        // Die .map()-Funktion von Page macht das sehr elegant
        return userPage.map(this::getUserProfile); // Wir können hier die existierende Methode wiederverwenden!
    }
}