package com.iris.backend.controller;

import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.model.User;
import com.iris.backend.service.GoogleApiService;
import com.iris.backend.service.PhotoService;
import com.iris.backend.service.GalleryFeedService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PhotoService photoService;
    private final GalleryFeedService galleryFeedService;
    private final GoogleApiService googleApiService;

    public PlaceController(PhotoService photoService,
                           GalleryFeedService galleryFeedService,
                           GoogleApiService googleApiService) {
        this.photoService = photoService;
        this.galleryFeedService = galleryFeedService;
        this.googleApiService = googleApiService;
    }


// --- GOOGLE PLACES ---

    /**
     * 1. Pictures from OTHERS (Public)
     * Pfad: places/google-places/$placeId/historical-photos
     */
    @PostMapping("/google-places/{placeId}/historical-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getHistoricalPhotosForGooglePlaceFromOthers(
            @PathVariable Long placeId,
            @RequestBody HistoricalSearchRequestDTO searchRequest,
            @AuthenticationPrincipal User currentUser // User wird benötigt zum Ausschließen
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForGooglePlaceFromOthers(
                placeId,
                searchRequest.history(),
                currentUser
        );
        return ResponseEntity.ok(photos);
    }

    /**
     * 2. YOUR shared Photos
     * Pfad: places/google-places/$placeId/historical-photos/my-photos
     */
    @PostMapping("/google-places/{placeId}/historical-photos/my-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getMyHistoricalPhotosForGooglePlace(
            @PathVariable Long placeId,
            @RequestBody HistoricalSearchRequestDTO searchRequest,
            @AuthenticationPrincipal User currentUser
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForGooglePlaceFromUser(
                placeId,
                searchRequest.history(),
                currentUser
        );
        return ResponseEntity.ok(photos);
    }


    // --- CUSTOM PLACES ---

    /**
     * 3. Pictures from OTHERS (Public)
     * Pfad: places/custom-places/$placeId/historical-photos
     */
    @PostMapping("/custom-places/{placeId}/historical-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getHistoricalPhotosForCustomPlaceFromOthers(
            @PathVariable UUID placeId,
            @RequestBody HistoricalSearchRequestDTO searchRequest,
            @AuthenticationPrincipal User currentUser
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForCustomPlaceFromOthers(
                placeId,
                searchRequest.history(),
                currentUser
        );
        return ResponseEntity.ok(photos);
    }

    /**
     * 4. YOUR shared Photos
     * Pfad: places/custom-places/$placeId/historical-photos/my-photos
     */
    @PostMapping("/custom-places/{placeId}/historical-photos/my-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getMyHistoricalPhotosForCustomPlace(
            @PathVariable UUID placeId,
            @RequestBody HistoricalSearchRequestDTO searchRequest,
            @AuthenticationPrincipal User currentUser
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForCustomPlaceFromUser(
                placeId,
                searchRequest.history(),
                currentUser
        );
        return ResponseEntity.ok(photos);
    }

    /**
     * Holt alle verfügbaren Orte (Google POIs + Iris Spots) für das Tagging
     * auf der Kamera-Seite.
     * Diese Abfrage ist schnell und gibt Orte AUCH OHNE Fotos zurück.
     *
     * @param latitude  Aktuelle Latitude des Benutzers
     * @param longitude Aktuelle Longitude des Benutzers
     * @return Eine Liste von Orten (DTOs), die zum Taggen verfügbar sind.
     */
    @GetMapping("/taggable")
    public ResponseEntity<List<GalleryFeedItemDTO>> getTaggablePlaces(
            @RequestParam double latitude,
            @RequestParam double longitude) {

        // Ruft die NEUE, SCHNELLE Service-Methode auf
        List<GalleryFeedItemDTO> taggablePlaces = galleryFeedService.getTaggablePlaces(latitude, longitude);
        return ResponseEntity.ok(taggablePlaces);
    }
}