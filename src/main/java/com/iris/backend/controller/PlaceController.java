package com.iris.backend.controller;

import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.service.GoogleApiService;
import com.iris.backend.service.PhotoService;
import com.iris.backend.service.GalleryFeedService;

import org.springframework.http.ResponseEntity;
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


    /**
     * Holt historische Fotos für einen Google Place.
     * Diese Methode bleibt unverändert, da sie den PhotoService nutzt.
     */
    @PostMapping("/google-places/{placeId}/historical-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getHistoricalPhotosForGooglePlace(
            @PathVariable Long placeId, // Erwartet eine Long ID
            @RequestBody HistoricalSearchRequestDTO searchRequest
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForGooglePlace(
                placeId,
                searchRequest.history()
        );
        return ResponseEntity.ok(photos);
    }


    /**
     * Holt historische Fotos für einen Custom Place (Iris Spot).
     * Diese Methode bleibt unverändert, da sie den PhotoService nutzt.
     */
    @PostMapping("/custom-places/{placeId}/historical-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getHistoricalPhotosForCustomPlace(
            @PathVariable UUID placeId, // Erwartet eine UUID
            @RequestBody HistoricalSearchRequestDTO searchRequest
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForCustomPlace(
                placeId,
                searchRequest.history()
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