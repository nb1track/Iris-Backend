package com.iris.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.*;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.repository.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final GooglePlaceRepository googlePlaceRepository;
    private final GcsStorageService gcsStorageService;
    private final UserRepository userRepository;
    private final FriendshipService friendshipService;
    private final CustomPlaceRepository customPlaceRepository;
    private final PhotoLikeRepository photoLikeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final String photosBucketName;
    private final String profileImagesBucketName;
    private final FriendshipRepository friendshipRepository;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;
    private final ChallengeCompletionRepository challengeCompletionRepository;
    private final CustomPlaceChallengeRepository challengeRepository;

    public PhotoService(
            //Repositories
            PhotoRepository photoRepository,
            GooglePlaceRepository googlePlaceRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            //Services
            @Lazy FriendshipService friendshipService,
            CustomPlaceRepository customPlaceRepository,
            PhotoLikeRepository photoLikeRepository,
            FcmService fcmService,
            ObjectMapper objectMapper,
            FriendshipRepository friendshipRepository,
            ChallengeCompletionRepository challengeCompletionRepository,
            CustomPlaceChallengeRepository challengeRepository,
            //Werte aus application.properties
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.photoRepository = photoRepository;
        this.googlePlaceRepository = googlePlaceRepository;
        this.gcsStorageService = gcsStorageService;
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
        this.customPlaceRepository = customPlaceRepository;
        this.photoLikeRepository = photoLikeRepository;
        this.fcmService = fcmService;
        this.objectMapper = objectMapper;
        this.challengeCompletionRepository = challengeCompletionRepository;
        this.challengeRepository = challengeRepository;
        this.friendshipRepository = friendshipRepository;
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude,
                            PhotoVisibility visibility, Long googlePlaceId, UUID customPlaceId,
                            User uploader, List<UUID> friendIds, UUID challengeId) {
        if (googlePlaceId != null && customPlaceId != null) {
            throw new IllegalArgumentException("A photo can only be linked to a Google Place or a Custom Place, not both.");
        }

        try {
            String objectName = gcsStorageService.uploadPhoto(file);
            Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            Photo newPhoto = new Photo();
            newPhoto.setUploader(uploader);
            newPhoto.setLocation(location);
            newPhoto.setVisibility(visibility);
            newPhoto.setStorageUrl(objectName);

            if (googlePlaceId != null) {
                GooglePlace place = googlePlaceRepository.findById(googlePlaceId)
                        .orElseThrow(() -> new RuntimeException("GooglePlace with ID " + googlePlaceId + " not found."));
                newPhoto.setGooglePlace(place);
            } else if (customPlaceId != null) {
                CustomPlace place = customPlaceRepository.findById(customPlaceId)
                        .orElseThrow(() -> new RuntimeException("CustomPlace with ID " + customPlaceId + " not found."));
                newPhoto.setCustomPlace(place);
            }

            OffsetDateTime now = OffsetDateTime.now();
            newPhoto.setUploadedAt(now);
            newPhoto.setExpiresAt(visibility == PhotoVisibility.PUBLIC ? now.plusHours(48) : now.plusDays(7));

            Photo savedPhoto = photoRepository.save(newPhoto);

            if (challengeId != null) {
                // Finde die Challenge-Instanz
                CustomPlaceChallenge challenge = challengeRepository.findById(challengeId)
                        .orElseThrow(() -> new RuntimeException("Challenge not found with ID: " + challengeId));

                // Erstelle den "Abschluss"-Eintrag
                ChallengeCompletion completion = new ChallengeCompletion();
                completion.setChallenge(challenge);
                completion.setUser(uploader);
                completion.setPhoto(savedPhoto); // Verknüpfe das gerade gespeicherte Foto

                challengeCompletionRepository.save(completion);
            }

            if (savedPhoto.getVisibility() == PhotoVisibility.FRIENDS) {
                List<User> friends = friendshipService.getFriendsAsEntities(uploader.getId());
                List<String> friendTokens = friends.stream()
                        .map(User::getFcmToken)
                        .filter(token -> token != null && !token.isEmpty())
                        .toList();

                fcmService.sendNewPhotoNotification(friendTokens, savedPhoto);
            }

            return savedPhoto.getId();
        } catch (IOException e) {
            throw new RuntimeException("Could not upload file: " + e.getMessage());
        }
    }

    // KORREKTUR: Ruft die richtige deleteFile-Methode in deinem GcsStorageService auf
    @Transactional
    public void deletePhoto(UUID photoId, User currentUser) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        if (!photo.getUploader().getId().equals(currentUser.getId())) {
            throw new SecurityException("User is not authorized to delete this photo.");
        }
        // Der Aufruf hier ist korrekt, da dein Service den Bucket-Namen als ersten Parameter erwartet
        gcsStorageService.deleteFile(photosBucketName, photo.getStorageUrl());
        photoRepository.delete(photo);
    }

    // Die restlichen Methoden bleiben wie von dir bereitgestellt
    /**
     * Holt eine Liste von Foto-DTOs, basierend auf einer Liste von IDs.
     * Prüft für jede ID die Berechtigung.
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> getPhotoDTOsByIds(List<UUID> photoIds, User currentUser) {
        if (photoIds == null || photoIds.isEmpty()) {
            return List.of();
        }

        // Rufe die 'getPhotoDTOById'-Methode für jede ID in der Liste auf.
        // Stream verarbeitet das parallel und effizient.
        return photoIds.stream()
                .map(photoId -> {
                    try {
                        // Rufe die Helfermethode auf, die die Sicherheitsprüfung enthält
                        return getPhotoDTOById(photoId, currentUser);
                    } catch (Exception e) {
                        // Wenn Foto nicht gefunden oder keine Berechtigung,
                        // gib null zurück.
                        // (Man könnte hier loggen: logger.warn("Konnte Foto {} für User {} nicht laden: {}", photoId, currentUser.getId(), e.getMessage());)
                        return null;
                    }
                })
                .filter(Objects::nonNull) // Filtere alle 'null'-Ergebnisse (ungültige/verbotene IDs) heraus
                .collect(Collectors.toList());
    }

    /**
     * Holt ein einzelnes Foto als DTO, wenn der Benutzer die Berechtigung hat.
     * (Wir ändern die Sichtbarkeit auf 'private', da sie nur noch intern genutzt wird)
     * KORREKTUR: TimelineService braucht sie auch. Wir lassen sie 'public'.
     */
    public PhotoResponseDTO getPhotoDTOById(UUID photoId, User currentUser) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        User uploader = photo.getUploader();

        // 1. Fall: Der Anfragende ist der Uploader selbst
        if (uploader.getId().equals(currentUser.getId())) {
            return toPhotoResponseDTO(photo);
        }

        // 2. Fall: Das Foto ist PUBLIC
        if (photo.getVisibility() == PhotoVisibility.PUBLIC) {
            return toPhotoResponseDTO(photo);
        }

        // 3. Fall: Das Foto ist FRIENDS-Only
        if (photo.getVisibility() == PhotoVisibility.FRIENDS) {
            Optional<Friendship> friendship = friendshipRepository.findFriendshipBetweenUsers(currentUser, uploader);

            if (friendship.isPresent() && friendship.get().getStatus() == FriendshipStatus.ACCEPTED) {
                return toPhotoResponseDTO(photo);
            }
        }

        // 4. Fall: Keine Berechtigung
        throw new SecurityException("User is not authorized to view this photo.");
    }
    @Transactional
    public void likePhoto(UUID photoId, User user) {
        photoRepository.findById(photoId).orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));
        PhotoLikeId likeId = new PhotoLikeId(user.getId(), photoId);
        if (photoLikeRepository.existsById(likeId)) { return; }
        PhotoLike newLike = new PhotoLike();
        newLike.setId(likeId);
        newLike.setUser(user);
        newLike.setPhoto(photoRepository.getReferenceById(photoId));
        photoLikeRepository.save(newLike);
    }

    public List<PhotoResponseDTO> getFriendsFeed(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<User> friends = friendshipService.getFriendsAsEntities(user.getId());
        if (friends.isEmpty()) { return List.of(); }
        List<Photo> photos = photoRepository.findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                friends, PhotoVisibility.FRIENDS, OffsetDateTime.now()
        );
        return photos.stream().map(this::toPhotoResponseDTO).collect(Collectors.toList());
    }

    // === GOOGLE PLACES ===

    /**
     * Holt Fotos von ANDEREN (Public).
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForGooglePlaceFromOthers(Long googlePlaceId, List<HistoricalPointDTO> history, User currentUser) {
        if (history == null || history.isEmpty()) return List.of();
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            // Ruft die neue Repo-Methode mit excludeUserId auf
            List<Photo> photos = photoRepository.findPhotosForGooglePlaceMatchingHistoricalBatchFromOthers(
                    googlePlaceId, historyJson, currentUser.getId()
            );
            return photos.stream().map(this::toPhotoResponseDTO).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    /**
     * Holt NUR MEINE Fotos.
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForGooglePlaceFromUser(Long googlePlaceId, List<HistoricalPointDTO> history, User currentUser) {
        if (history == null || history.isEmpty()) return List.of();
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            // Ruft die neue Repo-Methode mit targetUserId auf
            List<Photo> photos = photoRepository.findPhotosForGooglePlaceMatchingHistoricalBatchFromUser(
                    googlePlaceId, historyJson, currentUser.getId()
            );
            return photos.stream().map(this::toPhotoResponseDTO).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }


    // === CUSTOM PLACES ===

    /**
     * Holt Fotos von ANDEREN (Public) für Custom Places.
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForCustomPlaceFromOthers(UUID customPlaceId, List<HistoricalPointDTO> history, User currentUser) {
        if (history == null || history.isEmpty()) return List.of();
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Photo> photos = photoRepository.findPhotosForCustomPlaceMatchingHistoricalBatchFromOthers(
                    customPlaceId, historyJson, currentUser.getId()
            );
            return photos.stream().map(this::toPhotoResponseDTO).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    /**
     * Holt NUR MEINE Fotos für Custom Places.
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForCustomPlaceFromUser(UUID customPlaceId, List<HistoricalPointDTO> history, User currentUser) {
        if (history == null || history.isEmpty()) return List.of();
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Photo> photos = photoRepository.findPhotosForCustomPlaceMatchingHistoricalBatchFromUser(
                    customPlaceId, historyJson, currentUser.getId()
            );
            return photos.stream().map(this::toPhotoResponseDTO).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    /**
     * Wandelt eine Photo-Entity in das (jetzt saubere) PhotoResponseDTO um.
     * Diese Methode befüllt die neuen polymorphen Place-Felder korrekt.
     *
     * @param photo the photo entity to be converted into a PhotoResponseDTO
     * @return a PhotoResponseDTO containing detailed information about the photo, including signed URLs,
     * uploader details, place information (polymorphic), and dynamically loaded like count
     */
    public PhotoResponseDTO toPhotoResponseDTO(Photo photo) {
        User uploader = photo.getUploader();

        // 1. Signierte URLs generieren
        String signedPhotoUrl = gcsStorageService.generateSignedUrl(
                photosBucketName,
                photo.getStorageUrl(),
                15,
                TimeUnit.MINUTES
        );
        String signedProfileImageUrl = gcsStorageService.generateSignedUrl(
                profileImagesBucketName,
                uploader.getProfileImageUrl(),
                15,
                TimeUnit.MINUTES
        );

        // 2. NEUE Logik: Polymorphe Place-Informationen bestimmen
        GalleryPlaceType placeType = null;
        Long googlePlaceId = null;
        UUID customPlaceId = null;
        String placeName = "Friends Feed"; // Standard, falls weder POI noch Spot

        if (photo.getGooglePlace() != null) {
            placeType = GalleryPlaceType.GOOGLE_POI;
            googlePlaceId = photo.getGooglePlace().getId();
            placeName = photo.getGooglePlace().getName();
        } else if (photo.getCustomPlace() != null) {
            placeType = GalleryPlaceType.IRIS_SPOT;
            customPlaceId = photo.getCustomPlace().getId();
            placeName = photo.getCustomPlace().getName();
        }
        // (Wenn beide null sind, war es ein "FRIENDS" Upload ohne Ort -> placeName bleibt "Friends Feed")

        // 3. Like-Anzahl holen (unverändert)
        int currentLikeCount = photoLikeRepository.countByIdPhotoId(photo.getId());

        // 4. Das NEUE DTO zurückgeben
        return new PhotoResponseDTO(
                photo.getId(),
                signedPhotoUrl,
                photo.getUploadedAt(),
                placeType,
                googlePlaceId,
                customPlaceId,
                placeName,
                uploader.getId(),
                uploader.getUsername(),
                signedProfileImageUrl,
                currentLikeCount
        );
    }
}