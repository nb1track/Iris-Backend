package com.iris.backend.service;

import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.model.Place;
import com.iris.backend.model.TimelineEntry;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.repository.PlaceRepository;
import com.iris.backend.repository.TimelineEntryRepository;
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
    private final TimelineEntryRepository timelineEntryRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private final String photosBucketName;
    private final String profileImagesBucketName;

    public PhotoService(
            ObjectMapper objectMapper,
            PhotoRepository photoRepository,
            PlaceRepository placeRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            TimelineEntryRepository timelineEntryRepository,
            @Lazy FriendshipService friendshipService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName,
            @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName
    ) {
        this.objectMapper = objectMapper;
        this.photoRepository = photoRepository;
        this.placeRepository = placeRepository;
        this.gcsStorageService = gcsStorageService;
        this.userRepository = userRepository;
        this.timelineEntryRepository = timelineEntryRepository;
        this.friendshipService = friendshipService;
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, Long placeId, User uploader, List<UUID> friendIds) {
        try {
            Place selectedPlace = placeRepository.findById(placeId)
                    .orElseThrow(() -> new RuntimeException("Place with ID " + placeId + " not found."));

            String objectName = gcsStorageService.uploadPhoto(file);
            Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            Photo newPhoto = new Photo();
            newPhoto.setPlace(selectedPlace);
            newPhoto.setUploader(uploader);
            newPhoto.setLocation(location);
            newPhoto.setVisibility(visibility);
            newPhoto.setStorageUrl(objectName);

            OffsetDateTime now = OffsetDateTime.now();
            newPhoto.setUploadedAt(now);
            if (visibility == PhotoVisibility.PUBLIC) {
                newPhoto.setExpiresAt(now.plusHours(48));
            } else {
                newPhoto.setExpiresAt(now.plusDays(7));
            }

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
                .map(this::toPhotoResponseDTO)
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
                    .map(this::toPhotoResponseDTO)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }

    public PhotoResponseDTO toPhotoResponseDTO(Photo photo) {
        User uploader = photo.getUploader();

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

        // Wir holen den Ort und prüfen, ob er existiert.
        Place place = photo.getPlace();
        Integer placeId = null;
        String placeName = "Unknown Location"; // Standardwert, falls kein Ort vorhanden

        // Nur wenn der Ort nicht null ist, lesen wir seine Daten aus.
        if (place != null) {
            placeId = place.getId().intValue();
            placeName = place.getName();
        }

        return new PhotoResponseDTO(
                photo.getId(),
                signedPhotoUrl,
                photo.getUploadedAt(),
                placeId,
                placeName,
                uploader.getId(),
                uploader.getUsername(),
                signedProfileImageUrl
        );
    }
}