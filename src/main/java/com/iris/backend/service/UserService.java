package com.iris.backend.service;

import com.iris.backend.dto.*;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.BlockedNumberRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final BlockedNumberRepository blockedNumberRepository;
    private final GcsStorageService gcsStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Logger logger = LoggerFactory.getLogger(UserService.class); // NEU
    private final String photosBucketName;
    private final String profileImagesBucketName;

    public UserService(
            UserRepository userRepository,
            PhotoRepository photoRepository,
            FriendshipRepository friendshipRepository,
            BlockedNumberRepository blockedNumberRepository,
            GcsStorageService gcsStorageService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.blockedNumberRepository = blockedNumberRepository;
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

    @Transactional // Wichtig für Datenbank-Konsistenz
    public User registerNewUser(FirebaseToken decodedToken, SignUpRequestDTO signUpRequest) {
        if (userRepository.findByFirebaseUid(decodedToken.getUid()).isPresent()) {
            logger.warn("Attempted to register an already existing user with UID: {}", decodedToken.getUid());
            throw new IllegalStateException("User already exists in our database.");
        }

        User newUser = new User();
        newUser.setFirebaseUid(decodedToken.getUid());
        newUser.setEmail(decodedToken.getEmail());

        // Mapping der neuen Felder aus dem DTO
        newUser.setUsername(signUpRequest.username());
        newUser.setFirstname(signUpRequest.firstname());
        newUser.setLastname(signUpRequest.lastname());
        newUser.setPhoneNumber(signUpRequest.phoneNumber());

        // Profilbild Verarbeitung
        if (signUpRequest.base64Image() != null && !signUpRequest.base64Image().isBlank()) {
            try {
                // Bereinigen des Base64 Strings falls Header vorhanden sind (data:image/png;base64,...)
                String cleanBase64 = signUpRequest.base64Image();
                if (cleanBase64.contains(",")) {
                    cleanBase64 = cleanBase64.split(",")[1];
                }

                byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);

                // Upload in GCS
                String imageUrl = gcsStorageService.uploadProfileImage(decodedToken.getUid(), imageBytes);
                newUser.setProfileImageUrl(imageUrl);

            } catch (IllegalArgumentException e) {
                logger.error("Invalid base64 image provided for user {}", signUpRequest.username(), e);
                // Wir werfen hier keinen Fehler, damit die Registrierung nicht fehlschlägt,
                // nur weil das Bild kaputt ist. Der User hat dann einfach kein Bild.
            }
        }

        logger.info("--> Attempting to save new user '{}' with UID {}", signUpRequest.username(), decodedToken.getUid());
        User savedUser = userRepository.save(newUser);
        logger.info("<-- Successfully saved new user with database ID {}", savedUser.getId());
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
     * @param query       Der Suchbegriff.
     * @param currentUser Der eingeloggte Benutzer, der die Suche durchführt.
     * @param pageable    Paginierungsinformationen.
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

    /**
     * Prüft, ob ein Benutzer (basierend auf der Telefonnummer) für die Registrierung zugelassen ist.
     * Logik: Erlaubt, wenn die Nummer NICHT in der Sperrliste (blocked_numbers) steht.
     *
     * @param phoneNumber Die zu prüfende Telefonnummer.
     * @return true wenn der User NICHT blockiert ist, sonst false.
     */
    @Transactional(readOnly = true)
    public boolean checkAllowed(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        // Wenn die Nummer existiert, ist sie blockiert -> return false (nicht allowed)
        // Wenn sie NICHT existiert -> return true (allowed)
        return !blockedNumberRepository.existsByPhoneNumber(phoneNumber);
    }


    /**
     * Updates the profile image for a given user. Deletes the old profile image if it exists, uploads
     * the new one to cloud storage, updates the user's profile image URL, and then saves the user
     * entity with the updated profile image reference. Finally, it returns the updated user profile.
     *
     * @param user the user whose profile image is being updated
     * @param file the new profile image as a multipart file
     * @return the updated user profile as a UserDTO
     * @throws RuntimeException if there is an error processing the profile image
     */
    @Transactional
    public UserDTO updateProfileImage(User user, MultipartFile file) {
        try {
            // 1. Altes Bild löschen, falls vorhanden (Clean Code / Kostenersparnis)
            String oldImageName = user.getProfileImageUrl();
            if (oldImageName != null && !oldImageName.isBlank()) {
                // Wir löschen das alte Bild aus dem Bucket
                gcsStorageService.deleteFile(profileImagesBucketName, oldImageName);
            }

            // 2. Neues Bild hochladen (nutzt jetzt die neue Methode mit Unique-Name)
            String newObjectName = gcsStorageService.uploadProfileImage(user.getFirebaseUid(), file);

            // 3. User in der Datenbank aktualisieren
            user.setProfileImageUrl(newObjectName);
            User savedUser = userRepository.save(user);

            // 4. Aktualisiertes Profil (als DTO) zurückgeben
            return getUserProfile(savedUser);

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload new profile image: " + e.getMessage(), e);
        }
    }
}