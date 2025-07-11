package com.chaptime.backend.service;

import com.chaptime.backend.dto.ExportedFriendshipDTO;
import com.chaptime.backend.dto.ExportedPhotoDTO;
import com.chaptime.backend.dto.LocationUpdateRequestDTO;
import com.chaptime.backend.dto.UserDataExportDTO;
import com.chaptime.backend.model.Friendship;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.FriendshipStatus;
import com.chaptime.backend.repository.FriendshipRepository;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.firebase.auth.FirebaseToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Constructs a new instance of {@code UserService}.
     *
     * @param userRepository A data repository for {@code User} entities, used to
     *                       manage user-related database operations.
     * @param photoRepository A data repository for {@code Photo} entities, used to
     *                        manage operations related to user-uploaded photos.
     * @param friendshipRepository A data repository for {@code Friendship} entities,
     *                              used to handle user friendship data and relationships.
     * @param gcsStorageService A service for interacting with Google Cloud Storage (GCS),
     *                          used to manage the storage and retrieval of user-uploaded resources.
     */
    public UserService(UserRepository userRepository, PhotoRepository photoRepository, FriendshipRepository friendshipRepository, GcsStorageService gcsStorageService) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService; // Hinzufügen
    }

    /**
     * Updates the location of a user in the system with new geographical coordinates.
     * This method retrieves the user from the database by their unique identifier,
     * creates a new geometry point from the provided coordinates, and updates the
     * user's location and timestamp.
     *
     * @param userId The unique identifier (UUID) of the user whose location is being updated.
     * @param locationUpdate A {@code LocationUpdateRequestDTO} containing the new latitude and
     *                       longitude coordinates for the user's location.
     */
    public void updateUserLocation(UUID userId, LocationUpdateRequestDTO locationUpdate) {
        // 1. Finde den Benutzer in der Datenbank
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // 2. Erstelle einen Geometrie-Punkt aus den neuen Koordinaten
        Point newLocation = geometryFactory.createPoint(new Coordinate(locationUpdate.longitude(), locationUpdate.latitude()));

        // 3. Aktualisiere die Felder und speichere den Benutzer
        user.setLastLocation(newLocation);
        user.setLastLocationUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    /**
     * Exports user data into a unified data transfer object (DTO).
     * This includes user's profile information, photos, and accepted friendships.
     *
     * @param userId The unique identifier (UUID) of the user whose data is being exported.
     * @return A {@code UserDataExportDTO} containing the user's ID, username, email,
     *         uploaded photos, and accepted friendships.
     */
    public UserDataExportDTO exportUserData(UUID userId) {
        // 1. User-Profil holen
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Alle Fotos des Users holen
        List<Photo> photos = photoRepository.findAllByUploader(user);
        List<ExportedPhotoDTO> photoDTOs = photos.stream()
                .map(p -> new ExportedPhotoDTO(
                        p.getId(),
                        p.getStorageUrl(),
                        p.getVisibility(),
                        p.getUploadedAt(),
                        p.getLocation().getY(), // Latitude ist Y
                        p.getLocation().getX()  // Longitude ist X
                ))
                .collect(Collectors.toList());

        // 3. Alle Freunde des Users holen
        List<Friendship> friendships = friendshipRepository
                .findByUserOneAndStatusOrUserTwoAndStatus(user, FriendshipStatus.ACCEPTED, user, FriendshipStatus.ACCEPTED);
        List<ExportedFriendshipDTO> friendDTOs = friendships.stream()
                .map(f -> {
                    User friend = f.getUserOne().getId().equals(userId) ? f.getUserTwo() : f.getUserOne();
                    return new ExportedFriendshipDTO(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());

        // 4. Alles zu einem grossen DTO zusammenbauen
        return new UserDataExportDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                photoDTOs,
                friendDTOs
        );
    }

    /**
     * Deletes a user account and all associated resources from the system.
     * This includes:
     * - Removing the user entity from the database.
     * - Deleting all photos uploaded by the user from storage.
     * - Automatically removing related database entries, such as friendships,
     *   using "ON DELETE CASCADE".
     *
     * Note: User data is also planned to be removed from Firebase Authentication
     * in a future step.
     *
     * @param userId The unique identifier (UUID) of the user to be deleted.
     */
    @Transactional
    public void deleteUserAccount(UUID userId) {
        // 1. Finde den User, um sicherzustellen, dass er existiert
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Finde alle Fotos des Users, um sie aus dem Storage zu löschen
        List<Photo> photosToDelete = photoRepository.findAllByUploader(user);
        for (Photo photo : photosToDelete) {
            gcsStorageService.deleteFile(photo.getStorageUrl());
        }

        // 3. Lösche den User aus der Datenbank.
        // Dank "ON DELETE CASCADE" werden alle verknüpften Fotos, Freundschaften etc.
        // automatisch mitgelöscht.
        userRepository.delete(user);

        // 4. Späterer Schritt: Lösche den User aus Firebase Auth
    }

    /**
     * Registers a new user in the system based on a provided Firebase token and username.
     * The method checks if a user with the same Firebase UID already exists in the database.
     * If a user with the given UID is found, an {@code IllegalStateException} is thrown.
     * Otherwise, a new {@code User} entity is created using the Firebase token details and
     * the specified username, which is then saved in the database.
     *
     * @param decodedToken A {@code FirebaseToken} object containing the Firebase user's
     *                     authentication details, such as UID and email.
     * @param username     The desired username for the new user.
     * @return The newly created {@code User} entity after being saved in the database.
     * @throws IllegalStateException If a user with the same Firebase UID already exists in the database.
     */
    public User registerNewUser(FirebaseToken decodedToken, String username) {
        // Prüfen, ob der User oder der Username bereits existiert
        if (userRepository.findByFirebaseUid(decodedToken.getUid()).isPresent()) {
            throw new IllegalStateException("User already exists in our database.");
        }
        // Hier könntest du auch prüfen, ob der Username schon vergeben ist

        User newUser = new User();
        newUser.setFirebaseUid(decodedToken.getUid());
        newUser.setEmail(decodedToken.getEmail());
        newUser.setUsername(username);

        return userRepository.save(newUser);
    }

    /**
     * Retrieves a list of users who are geographically near the specified location,
     * excluding the user identified by the provided unique identifier.
     *
     * @param latitude The latitude coordinate of the location to search nearby users.
     * @param longitude The longitude coordinate of the location to search nearby users.
     * @param currentUserId The unique identifier (UUID) of the user to be excluded from the results.
     * @return A list of {@code User} entities representing users located within a specified radius
     *         from the given coordinates.
     */
    public List<User> getNearbyUsers(double latitude, double longitude, UUID currentUserId) {
        // Wir definieren einen festen Radius von z.B. 10 Kilometern (10000 Meter)
        final double SEARCH_RADIUS_METERS = 10000.0;
        return userRepository.findUsersNearby(latitude, longitude, SEARCH_RADIUS_METERS, currentUserId);
    }
}