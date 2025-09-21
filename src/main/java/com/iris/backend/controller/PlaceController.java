package com.iris.backend.controller;

import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.service.GoogleApiService;
import com.iris.backend.service.PhotoService;
import com.iris.backend.service.PlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.iris.backend.dto.CreatePlaceRequestDTO;
import com.iris.backend.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final GoogleApiService googleApiService;
    private final PhotoService photoService;
    private final PlaceService placeService;


    /**
     * Constructs a new PlaceController with the provided GoogleApiService and PhotoService.
     *
     * @param googleApiService the service used to handle interactions with the Google API
     * @param photoService the service used to handle photo-related operations
     */
    public PlaceController(GoogleApiService googleApiService,  PhotoService photoService, PlaceService placeService) {
        this.googleApiService = googleApiService;
        this.photoService = photoService;
        this.placeService = placeService;
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

        // KORREKTUR: Rufe die umbenannte und korrekte Methode im Service auf.
        List<PlaceDTO> nearbyPlaces = placeService.findActiveNearbyPlaces(latitude, longitude);
        return ResponseEntity.ok(nearbyPlaces);
    }

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

    // --- NEU: Eindeutiger Endpunkt für Custom Places ---
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