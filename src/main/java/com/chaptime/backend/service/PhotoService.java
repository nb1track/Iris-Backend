package com.chaptime.backend.service;

import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.PhotoVisibility;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.repository.PlaceRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PlaceRepository placeRepository;
    private final FriendshipService friendshipService;
    private final GoogleApiService googleApiService;
    private final GcsStorageService gcsStorageService;

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
    public PhotoService(PhotoRepository photoRepository, PlaceRepository placeRepository, FriendshipService friendshipService, GoogleApiService googleApiService, GcsStorageService gcsStorageService) {
        this.photoRepository = photoRepository;
        this.placeRepository = placeRepository;
        this.friendshipService = friendshipService;
        this.googleApiService = googleApiService;
        this.gcsStorageService = gcsStorageService;
    }

    /**
     * Creates and saves a new photo in the system.
     *
     * @param file the photo file to be uploaded
     * @param latitude the latitude of the photo's location
     * @param longitude the longitude of the photo's location
     * @param visibility the visibility level of the photo (e.g., PUBLIC or FRIENDS)
     * @param placeId the ID of the place associated with the photo
     * @param user the user uploading the photo
     * @return the unique identifier of the newly created photo
     * @throws RuntimeException if the place is not found, the file upload fails, or other errors occur during processing
     */
    @Transactional
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, Long placeId, User user) {
        try {
            // 1. Finde den vom User ausgewählten Ort in unserer Datenbank
            Place selectedPlace = placeRepository.findById(placeId)
                    .orElseThrow(() -> new RuntimeException("Place with ID " + placeId + " not found."));

            // 2. Bild-URL und Geo-Punkt erstellen
            String fileUrl = gcsStorageService.uploadFile(file);
            Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

            // 3. Photo-Objekt erstellen und befüllen
            Photo newPhoto = new Photo();
            newPhoto.setPlace(selectedPlace);
            newPhoto.setUploader(user);
            newPhoto.setLocation(location);
            newPhoto.setVisibility(visibility);
            newPhoto.setStorageUrl(fileUrl);

            // 4. Ablaufdatum berechnen
            OffsetDateTime now = OffsetDateTime.now();
            newPhoto.setUploadedAt(now);
            if (visibility == PhotoVisibility.PUBLIC) {
                newPhoto.setExpiresAt(now.plusHours(48));
            } else {
                newPhoto.setExpiresAt(now.plusDays(7));
            }

            // 5. Foto in der Datenbank speichern
            Photo savedPhoto = photoRepository.save(newPhoto);

            return savedPhoto.getId();
        } catch (IOException e) {
            // Im Fehlerfall eine Exception werfen
            throw new RuntimeException("Could not upload file: " + e.getMessage());
        }
    }


    /**
     * Finds and retrieves a list of public photos that are discoverable within a specified radius
     * of a given geographic location defined by latitude and longitude.
     *
     * @param latitude the latitude of the center point for the search
     * @param longitude the longitude of the center point for the search
     * @param radiusInMeters the radius (in meters) within which to search for photos
     * @return a list of PhotoResponseDTO objects, where each DTO contains information
     *         about a discoverable photo such as its ID, storage URL, and uploader's username
     */
    public List<PhotoResponseDTO> findDiscoverablePhotos(double latitude, double longitude, double radiusInMeters) {
        List<Photo> photos = photoRepository.findPublicPhotosWithinRadius(latitude, longitude, radiusInMeters);
        return photos.stream()
                .map(photo -> new PhotoResponseDTO(
                        photo.getId(),
                        photo.getStorageUrl(),
                        photo.getUploader().getUsername()
                ))
                .collect(Collectors.toList());
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
     * Retrieves a list of public photos associated with a specific place.
     * The method ensures the place exists, then fetches photos that are visible to the public,
     * have not expired, and are ordered by upload time in descending order.
     *
     * @param placeId the unique identifier of the place for which photos are to be retrieved
     * @return a list of PhotoResponseDTO objects containing information about the photos
     *         (e.g., their ID, storage URL, and uploader's username)
     * @throws RuntimeException if the place with the specified ID is not found
     */
    public List<PhotoResponseDTO> getPhotosForPlace(Long placeId) {
        // Finde den Ort, um sicherzustellen, dass er existiert
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RuntimeException("Place not found"));

        // Finde alle passenden Fotos für diesen Ort
        List<Photo> photos = photoRepository
                .findAllByPlaceAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                        place,
                        PhotoVisibility.PUBLIC,
                        OffsetDateTime.now()
                );

        // Wandle die Ergebnisse in DTOs um
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
}