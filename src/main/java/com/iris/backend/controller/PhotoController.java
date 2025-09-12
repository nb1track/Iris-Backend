package com.iris.backend.controller;

import com.iris.backend.dto.PhotoResponseDTO;
import java.util.List;
import com.iris.backend.dto.PhotoUploadRequestDTO;
import com.iris.backend.dto.PhotoUploadResponse;
import com.iris.backend.model.User;
import com.iris.backend.repository.UserRepository;
import com.iris.backend.service.PhotoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper; // Jackson's Tool zum Umwandeln

    /**
     * Constructs a new instance of the PhotoController.
     * This controller is responsible for handling photo-related API endpoints,
     * such as uploading photos, retrieving discoverable photos, getting user feeds, and deleting photos.
     *
     * @param photoService the service used for managing photo-related operations
     * @param userRepository the repository used for user-related data management
     * @param objectMapper the object mapper used for JSON serialization and deserialization
     */
    public PhotoController(PhotoService photoService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.photoService = photoService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles the uploading of a photo along with its associated metadata.
     * The metadata JSON is parsed to extract location coordinates, visibility,
     * and place information while the user information is extracted from the
     * authentication token.
     *
     * @param uploader the authenticated user uploading the photo
     * @param file the photo file being uploaded
     * @param metadataJson the JSON string containing metadata about the photo,
     *                     such as latitude, longitude, visibility, and place ID
     * @return a {@link ResponseEntity} containing a {@link PhotoUploadResponse}
     *         with the newly created photo's unique ID
     * @throws JsonProcessingException if the metadata JSON string cannot be parsed
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @AuthenticationPrincipal User uploader,
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") String metadataJson
    ) throws Exception {

        // Dieses DTO hat die Felder googlePlaceId und customPlaceId
        PhotoUploadRequestDTO metadata = objectMapper.readValue(metadataJson, PhotoUploadRequestDTO.class);

        // KORREKTUR: Wir übergeben jetzt die richtigen Parameter an den Service
        UUID newPhotoId = photoService.createPhoto(
                file,
                metadata.latitude(),
                metadata.longitude(),
                metadata.visibility(),
                metadata.googlePlaceId(), // Korrekter Parameter
                metadata.customPlaceId(),  // Korrekter Parameter
                uploader,
                metadata.friendIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new PhotoUploadResponse(newPhotoId));
    }

    /**
     * Retrieves the feed of photos shared by friends of the authenticated user.
     *
     * @param user the authenticated user whose friends' feed is to be retrieved,
     *             injected automatically by Spring Security
     * @return a {@link ResponseEntity} containing a list of {@link PhotoResponseDTO} objects
     *         representing the photos in the user's friends' feed
     */
    @GetMapping("/feed")
    public ResponseEntity<List<PhotoResponseDTO>> getFriendsFeed(@AuthenticationPrincipal User user) {
        // Spring Security injiziert hier automatisch den angemeldeten User
        List<PhotoResponseDTO> feedPhotos = photoService.getFriendsFeed(user.getId());
        return ResponseEntity.ok(feedPhotos);
    }

    /**
     * Deletes a photo identified by its unique ID. The deletion is performed
     * only if the authenticated user is authorized to delete the photo.
     * Returns appropriate HTTP status codes based on the operation's outcome:
     * - 204 No Content if the deletion is successful.
     * - 403 Forbidden if the user is not authorized to delete the photo.
     * - 404 Not Found if the photo does not exist.
     *
     * @param photoId the unique identifier of the photo to be deleted
     * @param currentUser the currently authenticated user attempting the delete operation
     * @return a {@link ResponseEntity} with an appropriate HTTP status code indicating the result of the operation
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal User currentUser) {

        try {
            photoService.deletePhoto(photoId, currentUser);
            // Erfolgreiche Löschung, antworte mit 204 No Content
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            // User ist nicht der Besitzer, antworte mit 403 Forbidden
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            // Foto wurde nicht gefunden, antworte mit 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }
}