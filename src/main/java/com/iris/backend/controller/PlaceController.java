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

        // Rufe die neue Methode auf, die beide Quellen durchsucht
        List<PlaceDTO> nearbyPlaces = placeService.findNearbyCombinedPlaces(latitude, longitude);
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

    /**
     * Creates a new custom place based on user input.
     *
     * @param currentUser The authenticated user creating the place.
     * @param request The DTO containing the name and coordinates of the new place.
     * @return A ResponseEntity with the newly created PlaceDTO and HTTP status 201 (Created).
     */
    @PostMapping("/custom")
    public ResponseEntity<PlaceDTO> createCustomPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestBody @Valid CreatePlaceRequestDTO request) {

        // Wir übergeben die Anfrage an den Service, der die Logik enthält
        PlaceDTO newPlace = placeService.createCustomPlace(request, currentUser);

        // Wir geben den neu erstellten Ort an das Frontend zurück
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlace);
    }
}