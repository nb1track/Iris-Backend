package com.chaptime.backend.controller;

import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.service.GoogleApiService;
import com.chaptime.backend.service.PhotoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


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
    public ResponseEntity<PlaceDTO> getNearbyPlace( // Name und Rückgabetyp anpassen
                                                    @RequestParam double latitude,
                                                    @RequestParam double longitude) {

        // Methode mit korrektem Namen aufrufen und Optional behandeln
        return googleApiService.findNearbyPlace(latitude, longitude)
                .map(place -> ResponseEntity.ok(place))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves a list of public photos associated with a specific place.
     * The photos are ordered by upload time in descending order and include details
     * such as the photo ID, storage URL, and uploader's username. This endpoint is used
     * to fetch photos that are visible to the public and not expired.
     *
     * @param placeId the unique identifier of the place for which the photos are to be retrieved
     * @return a ResponseEntity containing a list of PhotoResponseDTO objects, each representing a photo
     */
    @GetMapping("/{placeId}/photos")
    public ResponseEntity<List<PhotoResponseDTO>> getPhotosForPlace(@PathVariable Long placeId) {
        List<PhotoResponseDTO> photos = photoService.getPhotosForPlace(placeId);
        return ResponseEntity.ok(photos);
    }
}