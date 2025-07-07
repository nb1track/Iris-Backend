package com.chaptime.backend.controller;

import com.chaptime.backend.dto.HistoricalSearchRequestDTO;
import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.service.GoogleApiService;
import com.chaptime.backend.service.PhotoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final GoogleApiService googleApiService;
    private final PhotoService photoService;

    /**
     * Constructs a new PlaceController with the provided GoogleApiService and PhotoService.
     *
     * @param googleApiService the service used to handle interactions with the Google API
     * @param photoService the service used to handle photo-related operations
     */
    public PlaceController(GoogleApiService googleApiService,  PhotoService photoService) {
        this.googleApiService = googleApiService;
        this.photoService = photoService;
    }

    /**
     * Retrieves a list of nearby places based on the provided geographic coordinates.
     *
     * @param latitude the latitude of the location to search for nearby places
     * @param longitude the longitude of the location to search for nearby places
     * @return a ResponseEntity containing a list of PlaceDTO objects representing nearby places
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<PlaceDTO>> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude) {

        // Rufe die Methode auf, die jetzt wieder eine Liste zurückgibt
        List<PlaceDTO> nearbyPlaces = googleApiService.findNearbyPlaces(latitude, longitude);
        return ResponseEntity.ok(nearbyPlaces);
    }

    /**
     * Retrieves a list of historical photos for a specific place based on the provided historical search data.
     *
     * @param placeId the unique identifier of the place for which historical photos are being requested
     * @param searchRequest the request payload containing historical search data, including a list of historical points
     * @return a ResponseEntity containing a list of PhotoResponseDTO objects representing the historical photos
     */
    @PostMapping("/{placeId}/historical-photos")
    public ResponseEntity<List<PhotoResponseDTO>> getHistoricalPhotosForPlace(
            @PathVariable Long placeId,
            @RequestBody HistoricalSearchRequestDTO searchRequest
    ) {
        List<PhotoResponseDTO> photos = photoService.findHistoricalPhotosForPlace(
                placeId,
                searchRequest.history()
        );
        return ResponseEntity.ok(photos);
    }
}