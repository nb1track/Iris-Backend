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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; // NEU
import org.slf4j.LoggerFactory; // NEU
import org.springframework.transaction.annotation.Transactional; // NEU

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Logger logger = LoggerFactory.getLogger(UserService.class); // NEU

    public UserService(UserRepository userRepository, PhotoRepository photoRepository, FriendshipRepository friendshipRepository, GcsStorageService gcsStorageService) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService;
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
            gcsStorageService.deleteFile(photo.getStorageUrl());
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
        List<User> usersInRadius = userRepository.findUsersNearby(
                latitude,
                longitude,
                radiusInMeters,
                currentUser.getId()
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
                    String signedProfileUrl = null;
                    // Prüfe, ob eine Bild-URL in der Datenbank existiert
                    if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                        // Generiere die temporäre, sichere URL.
                        // Der Dateiname ist die Firebase-UID.
                        signedProfileUrl = gcsStorageService.generateSignedUrlForProfilePicture(user.getFirebaseUid());
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
}