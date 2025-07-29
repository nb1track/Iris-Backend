package com.iris.backend.service;

import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.model.Place;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.repository.PlaceRepository;
import com.iris.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final ObjectMapper objectMapper;
    private final PhotoRepository photoRepository;
    private final PlaceRepository placeRepository;
    private final FriendshipService friendshipService;
    private final GcsStorageService gcsStorageService;
    private final UserRepository userRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // NEU: Bucket-Namen aus der Konfiguration injizieren
    private final String photosBucketName;
    private final String profileImagesBucketName;

    public PhotoService(
            ObjectMapper objectMapper,
            PhotoRepository photoRepository,
            PlaceRepository placeRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            @Lazy FriendshipService friendshipService,
            // NEU: Die Bucket-Namen werden hier übergeben
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.objectMapper = objectMapper;
        this.photoRepository = photoRepository;
        this.placeRepository = placeRepository;
        this.gcsStorageService = gcsStorageService;
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
        // NEU: Die Bucket-Namen speichern
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, Long placeId, User uploader) {
        try {
            Place selectedPlace = placeRepository.findById(placeId)
                    .orElseThrow(() -> new RuntimeException("Place with ID " + placeId + " not found."));

            // GEÄNDERT: Ruft die neue, spezifische Upload-Methode auf
            String objectName = gcsStorageService.uploadPhoto(file);
            Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            Photo newPhoto = new Photo();
            newPhoto.setPlace(selectedPlace);
            newPhoto.setUploader(uploader);
            newPhoto.setLocation(location);
            newPhoto.setVisibility(visibility);
            // GEÄNDERT: Speichert nur noch den Objektnamen, nicht die ganze URL
            newPhoto.setStorageUrl(objectName);

            OffsetDateTime now = OffsetDateTime.now();
            newPhoto.setUploadedAt(now);
            if (visibility == PhotoVisibility.PUBLIC) {
                newPhoto.setExpiresAt(now.plusHours(48));
            } else {
                newPhoto.setExpiresAt(now.plusDays(7));
            }

            Photo savedPhoto = photoRepository.save(newPhoto);
            return savedPhoto.getId();
        } catch (IOException e) {
            throw new RuntimeException("Could not upload file: " + e.getMessage());
        }
    }

    @Transactional
    public void deletePhoto(UUID photoId, User currentUser) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        if (!photo.getUploader().getId().equals(currentUser.getId())) {
            throw new SecurityException("User is not authorized to delete this photo.");
        }

        // GEÄNDERT: Ruft die neue deleteFile-Methode mit Bucket- und Objektnamen auf
        gcsStorageService.deleteFile(photosBucketName, photo.getStorageUrl());

        photoRepository.delete(photo);
    }

    public List<PhotoResponseDTO> getFriendsFeed(UUID userId) {
        List<User> friends = friendshipService.getFriendsAsEntities(userId);
        if (friends.isEmpty()) {
            return List.of();
        }
        List<Photo> photos = photoRepository
                .findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                        friends,
                        PhotoVisibility.FRIENDS,
                        OffsetDateTime.now()
                );
        return photos.stream()
                .map(this::toPhotoResponseDTO) // Nutzt die zentrale Hilfsmethode
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForPlace(Long placeId, List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Photo> photos = photoRepository.findPhotosForPlaceMatchingHistoricalBatch(placeId, historyJson);
            return photos.stream()
                    .map(this::toPhotoResponseDTO) // Nutzt die zentrale Hilfsmethode
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    /**
     * Die zentrale Hilfsmethode.
     * Konvertiert eine Photo-Entität in ein DTO und generiert signierte URLs
     * für das Hauptfoto UND das Profilbild des Uploaders.
     */
    private PhotoResponseDTO toPhotoResponseDTO(Photo photo) {
        User uploader = photo.getUploader();

        // 1. Signierte URL für das Hauptfoto generieren (z.B. 1 Stunde gültig)
        String signedPhotoUrl = gcsStorageService.generateSignedUrl(
                photosBucketName,
                photo.getStorageUrl(),
                1,
                TimeUnit.HOURS
        );

        // 2. Signierte URL für das Profilbild des Uploaders generieren (z.B. 1 Stunde gültig)
        String signedProfileImageUrl = gcsStorageService.generateSignedUrl(
                profileImagesBucketName,
                uploader.getProfileImageUrl(),
                1,
                TimeUnit.HOURS
        );

        return new PhotoResponseDTO(
                photo.getId(),
                signedPhotoUrl,
                photo.getUploadedAt(),
                photo.getPlace().getId().intValue(),
                photo.getPlace().getName(),
                uploader.getId(),
                uploader.getUsername(),
                signedProfileImageUrl
        );
    }
}