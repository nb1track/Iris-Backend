package com.iris.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.*;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final GooglePlaceRepository googlePlaceRepository;
    private final GcsStorageService gcsStorageService;
    private final UserRepository userRepository;
    private final TimelineEntryRepository timelineEntryRepository;
    private final FriendshipService friendshipService;
    private final CustomPlaceRepository customPlaceRepository;
    private final PhotoLikeRepository photoLikeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final String photosBucketName;
    private final String profileImagesBucketName;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    public PhotoService(
            //Repositories
            PhotoRepository photoRepository,
            GooglePlaceRepository googlePlaceRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            TimelineEntryRepository timelineEntryRepository,
            //Services
            @Lazy FriendshipService friendshipService,
            CustomPlaceRepository customPlaceRepository,
            PhotoLikeRepository photoLikeRepository,
            FcmService fcmService,
            ObjectMapper objectMapper,
            //Werte aus application.properties
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.photoRepository = photoRepository;
        this.googlePlaceRepository = googlePlaceRepository;
        this.gcsStorageService = gcsStorageService;
        this.userRepository = userRepository;
        this.timelineEntryRepository = timelineEntryRepository;
        this.friendshipService = friendshipService;
        this.customPlaceRepository = customPlaceRepository;
        this.photoLikeRepository = photoLikeRepository;
        this.fcmService = fcmService;
        this.objectMapper = objectMapper;
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, Long googlePlaceId, UUID customPlaceId, User uploader, List<UUID> friendIds) {
        if (googlePlaceId != null && customPlaceId != null) {
            throw new IllegalArgumentException("A photo can only be linked to a Google Place or a Custom Place, not both.");
        }

        try {
            // KORREKTUR: Ruft die richtige Methode in deinem GcsStorageService auf
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

            if (visibility == PhotoVisibility.FRIENDS && friendIds != null && !friendIds.isEmpty()) {
                List<User> targetFriends = userRepository.findAllById(friendIds);
                for (User friend : targetFriends) {
                    TimelineEntry newEntry = new TimelineEntry();
                    newEntry.setUser(friend);
                    newEntry.setPhoto(savedPhoto);
                    timelineEntryRepository.save(newEntry);
                }
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

    /**
     * Retrieves a list of historical photos related to a specific Google Place, utilizing provided historical data points.
     *
     * @param googlePlaceId the unique identifier for the Google Place
     * @param history the list of historical data points to match against when fetching photos
     * @return a list of PhotoResponseDTO objects representing the photos associated with the given Google Place and historical data
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForGooglePlace(Long googlePlaceId, List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            // Ruft die neue, spezifische Repository-Methode auf
            List<Photo> photos = photoRepository.findPhotosForGooglePlaceMatchingHistoricalBatch(googlePlaceId, historyJson);
            return photos.stream()
                    .map(this::toPhotoResponseDTO)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    /**
     * Finds and retrieves historical photos for a specific custom place based on the provided historical data points.
     *
     * @param customPlaceId the unique identifier of the custom place for which historical photos are to be retrieved
     * @param history a list of historical data points represented as HistoricalPointDTO objects
     * @return a list of PhotoResponseDTO containing the details of the historical photos, or an empty list if no applicable photos are found
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForCustomPlace(UUID customPlaceId, List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            // Ruft die neue, spezifische Repository-Methode auf
            List<Photo> photos = photoRepository.findPhotosForCustomPlaceMatchingHistoricalBatch(customPlaceId, historyJson);
            return photos.stream()
                    .map(this::toPhotoResponseDTO)
                    .collect(Collectors.toList());
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

                // --- NEUE FELDER ---
                placeType,
                googlePlaceId,
                customPlaceId,
                // --- ENDE NEUE FELDER ---

                placeName,
                uploader.getId(),
                uploader.getUsername(),
                signedProfileImageUrl,
                currentLikeCount
        );
    }
}