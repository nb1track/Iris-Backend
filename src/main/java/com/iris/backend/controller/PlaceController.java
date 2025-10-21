package com.iris.backend.controller;

import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
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
    private final GalleryFeedService galleryFeedService; // NEU

    // Konstruktor bereinigt: GoogleApiService und PlaceService sind raus.
    public PlaceController(PhotoService photoService, GalleryFeedService galleryFeedService) {
        this.photoService = photoService;
        this.galleryFeedService = galleryFeedService;
    }

    /**
     * NEU: Holt den "Entdeckte Spots"-Feed (POIs + Iris Spots).
     * Nutzt den neuen GalleryFeedService und das GalleryFeedItemDTO.
     * Ersetzt die alte getNearbyPlaces-Logik.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<GalleryFeedItemDTO>> getNearbyPlaces( // NEUER RÜCKGABETYP
                                                                     @RequestParam double latitude,
                                                                     @RequestParam double longitude) {

        // NEUE LOGIK: Ruft den neuen zentralen Service auf
        List<GalleryFeedItemDTO> discoveredSpots = galleryFeedService.getDiscoveredSpots(latitude, longitude);
        return ResponseEntity.ok(discoveredSpots);
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

}