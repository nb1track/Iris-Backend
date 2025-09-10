package com.iris.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
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

    public PhotoService(
            PhotoRepository photoRepository,
            GooglePlaceRepository googlePlaceRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            TimelineEntryRepository timelineEntryRepository,
            @Lazy FriendshipService friendshipService,
            CustomPlaceRepository customPlaceRepository,
            PhotoLikeRepository photoLikeRepository,
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

    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForPlace(Long placeId, List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            // HINWEIS: ObjectMapper muss als Abhängigkeit im Konstruktor vorhanden sein.
            ObjectMapper objectMapper = new ObjectMapper();
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
        String signedPhotoUrl = gcsStorageService.generateSignedUrl(photosBucketName, photo.getStorageUrl(), 15, TimeUnit.MINUTES);
        String signedProfileImageUrl = gcsStorageService.generateSignedUrl(profileImagesBucketName, uploader.getProfileImageUrl(), 15, TimeUnit.MINUTES);

        Integer googlePlaceId = null;
        String placeName = "Custom Location";
        UUID customPlaceId = null;

        if (photo.getGooglePlace() != null) {
            googlePlaceId = photo.getGooglePlace().getId().intValue();
            placeName = photo.getGooglePlace().getName();
        } else if (photo.getCustomPlace() != null) {
            customPlaceId = photo.getCustomPlace().getId();
            placeName = photo.getCustomPlace().getName();
        }

        // Annahme: Dein DTO wird so angepasst, dass es damit umgehen kann.
        return new PhotoResponseDTO(photo.getId(), signedPhotoUrl, photo.getUploadedAt(), googlePlaceId, placeName, uploader.getId(), uploader.getUsername(), signedProfileImageUrl);
    }
}