package com.chaptime.backend.service;

import com.chaptime.backend.dto.HistoricalPointDTO;
import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.PhotoVisibility;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.repository.PlaceRepository;
import com.chaptime.backend.repository.TimelineEntryRepository;
import com.chaptime.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final ObjectMapper objectMapper;
    private final PhotoRepository photoRepository;
    private final PlaceRepository placeRepository;
    private final FriendshipService friendshipService;
    private final GoogleApiService googleApiService;
    private final GcsStorageService gcsStorageService;
    private final UserRepository userRepository;
    private final TimelineEntryRepository timelineEntryRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Constructs a new PhotoService with the necessary dependencies for managing photos.
     *
     * @param photoRepository the repository for handling photo data operations
     * @param placeRepository the repository for handling place data operations
     * @param friendshipService the service for managing user friendships
     * @param googleApiService the service for interacting with Google APIs
     * @param gcsStorageService the service for handling file storage operations in Google Cloud Storage
     */
    public PhotoService(
            ObjectMapper objectMapper,
            PhotoRepository photoRepository,
            PlaceRepository placeRepository,
            GcsStorageService gcsStorageService,
            UserRepository userRepository,
            TimelineEntryRepository timelineEntryRepository,
            @Lazy FriendshipService friendshipService,
            GoogleApiService googleApiService
    ) {
        this.objectMapper = objectMapper;
        this.photoRepository = photoRepository;
        this.placeRepository = placeRepository;
        this.gcsStorageService = gcsStorageService;
        this.userRepository = userRepository;
        this.timelineEntryRepository = timelineEntryRepository;
        this.friendshipService = friendshipService;
        this.googleApiService = googleApiService;
    }

    /**
     * Creates a new photo, uploads the file to storage, associates it with a place,
     * user, visibility, and location, and optionally shares it with designated friends.
     *
     * @param file       The photo file to be uploaded.
     * @param latitude   The latitude coordinate of the photo's location.
     * @param longitude  The longitude coordinate of the photo's location.
     * @param visibility The visibility level of the photo (PUBLIC, PRIVATE, or FRIENDS).
     * @param placeId    The ID of the place associated with the photo.
     * @param uploader   The user who is uploading the photo.
     * @param friendIds  A list of friend IDs with whom the photo is optionally shared
     *                   when visibility is set to FRIENDS.
     * @return The unique identifier (UUID) of the newly created photo.
     * @throws RuntimeException If the place cannot be found, if the file upload fails,
     *                          or other processing errors occur.
     */
    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, Long placeId, User uploader, List<UUID> friendIds) {
        try {
            Place selectedPlace = placeRepository.findById(placeId)
                    .orElseThrow(() -> new RuntimeException("Place with ID " + placeId + " not found."));

            String fileUrl = gcsStorageService.uploadFile(file);
            Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            Photo newPhoto = new Photo();
            newPhoto.setPlace(selectedPlace);
            newPhoto.setUploader(uploader);
            newPhoto.setLocation(location);
            newPhoto.setVisibility(visibility);
            newPhoto.setStorageUrl(fileUrl);

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

    /**
     * Retrieves a list of photos that are visible to the specified user's friends.
     * This method fetches the user's friends, then retrieves photos uploaded
     * by those friends with the visibility set to "FRIENDS" and that have not yet expired.
     * The photos are returned in descending order of their upload time.
     *
     * @param userId the unique identifier of the user whose friends' feed is to be fetched
     * @return a list of PhotoResponseDTO objects containing details of the photos in the friends' feed;
     *         if the user has no friends or there are no valid photos, an empty list is returned
     */
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
                .map(photo -> new PhotoResponseDTO(
                        photo.getId(),
                        photo.getStorageUrl(),
                        photo.getUploader().getUsername()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Deletes a photo specified by its unique identifier.
     * This method performs a security check to ensure that the photo can
     * only be deleted by the user who uploaded it. The photo data is
     * removed from the database and the associated file is deleted from
     * Google Cloud Storage.
     *
     * @param photoId the unique identifier of the photo to be deleted
     * @param currentUser the user currently authenticated, required to
     *                    validate ownership of the photo
     * @throws RuntimeException if the photo with the specified ID is not found
     * @throws SecurityException if the authenticated user is not authorized
     *                           to delete the photo
     */
    @Transactional
    public void deletePhoto(UUID photoId, User currentUser) {
        // 1. Finde das Foto in der Datenbank
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        // 2. SICHERHEITS-CHECK: Gehört das Foto dem angemeldeten Benutzer?
        if (!photo.getUploader().getId().equals(currentUser.getId())) {
            throw new SecurityException("User is not authorized to delete this photo.");
        }

        // 3. Lösche die Datei aus Google Cloud Storage
        gcsStorageService.deleteFile(photo.getStorageUrl());

        // 4. Lösche den Eintrag aus der Datenbank
        photoRepository.delete(photo);
    }

    /**
     * Finds historical photos for a specific place based on the given list of historical points.
     *
     * @param placeId the unique identifier of the place for which historical photos are being requested
     * @param history a list of historical points containing data to match historical photos for the specified place
     * @return a list of PhotoResponseDTO objects containing information about historical photos matching the given place and historical points;
     *         returns an empty list if no matching photos are found or if the history list is null or empty
     */
    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> findHistoricalPhotosForPlace(Long placeId, List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            // Wandle die Liste der Punkte in einen JSON-String um
            String historyJson = objectMapper.writeValueAsString(history);

            List<Photo> photos = photoRepository.findPhotosForPlaceMatchingHistoricalBatch(placeId, historyJson);

            return photos.stream()
                    .map(photo -> new PhotoResponseDTO(
                            photo.getId(),
                            photo.getStorageUrl(),
                            photo.getUploader().getUsername()
                    ))
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            // Hier könntest du einen Fehler loggen
            throw new RuntimeException("Error processing historical photo data", e);
        }
    }
}