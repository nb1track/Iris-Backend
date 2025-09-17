package com.iris.backend.controller;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.service.CustomPlaceService;
import com.iris.backend.service.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/custom-places")
public class CustomPlaceController {

    private final CustomPlaceService customPlaceService;
    private final CustomPlaceRepository customPlaceRepository;
    private final PlaceService placeService;



    public CustomPlaceController(CustomPlaceService customPlaceService, CustomPlaceRepository customPlaceRepository, PlaceService placeService) {
        this.customPlaceService = customPlaceService;
        this.customPlaceRepository = customPlaceRepository;
        this.placeService = placeService;
    }

    /**
     * Creates a new custom place based on the provided request and the currently authenticated user.
     *
     * This method allows an authenticated user to create a new custom place by providing the necessary
     * details in the request payload. The newly created place is stored, and its unique identifier is returned.
     *
     * @param currentUser The authenticated user creating the custom place.
     * @param request A {@code CreateCustomPlaceRequestDTO} object containing the details of the custom place to be created.
     * @return A {@code ResponseEntity} containing the UUID of the newly created custom place and a
     *         status code of 201 (Created) upon successful creation.
     */
    @PostMapping
    public ResponseEntity<UUID> createCustomPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestBody CreateCustomPlaceRequestDTO request
    ) {
        CustomPlace newPlace = customPlaceService.createCustomPlace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlace.getId());
    }

    /**
     * Retrieves the list of custom places created by the currently authenticated user.
     *
     * This method fetches custom places authored by the specified user,
     * ordered by their creation time in descending order, and maps them
     * into a list of PlaceDTO objects for response purposes.
     *
     * @param currentUser The authenticated user making the request.
     * @return A ResponseEntity containing a list of {@code PlaceDTO} objects
     *         representing the custom places created by the user.
     */
    @GetMapping("/my-spots")
    public ResponseEntity<List<PlaceDTO>> getMyCreatedSpots(@AuthenticationPrincipal User currentUser) {
        List<CustomPlace> mySpotsEntities = customPlaceRepository.findAllByCreatorOrderByCreatedAtDesc(currentUser);

        // NEU: Wandle die Entitäten in dein vollständiges PlaceDTO um
        List<PlaceDTO> mySpotsDTOs = mySpotsEntities.stream()
                .map(spot -> new PlaceDTO(
                        null, // Custom Places haben keine Long-ID
                        "custom_" + spot.getId().toString(), // Eine eindeutige ID für das Frontend
                        spot.getName(),
                        "Custom Location", // Ein passender Platzhalter für die Adresse
                        null, // In dieser Übersicht werden keine Fotos geladen
                        spot.getRadiusMeters(), // Radius aus dem CustomPlace-Objekt übernehmen
                        0 // Setze eine Standard-Wichtigkeit, da Custom Places dieses Feld nicht haben
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(mySpotsDTOs);
    }

    @GetMapping("/trending")
    public ResponseEntity<List<PlaceDTO>> getTrendingSpots() {
        List<PlaceDTO> trendingSpots = placeService.findTrendingSpots(); // Diese Methode erstellen wir jetzt
        return ResponseEntity.ok(trendingSpots);
    }

    /**
     * Retrieves the list of participants for a specific custom place.
     *
     * This method handles requests to fetch users associated with a particular custom place
     * identified by its unique ID. The requesting user must have appropriate permissions
     * to view the participants.
     *
     * @param placeId The unique identifier (UUID) of the custom place for which participants are to be retrieved.
     * @param currentUser The authenticated user making the request.
     * @return A ResponseEntity containing a list of {@code UserDTO} objects representing the participants,
     *         or an appropriate HTTP status code:
     *         - 200 OK if the participants are successfully retrieved.
     *         - 403 Forbidden if the authenticated user lacks sufficient*/
    @GetMapping("/{placeId}/participants")
    public ResponseEntity<List<UserDTO>> getParticipantsForPlace(
            @PathVariable UUID placeId,
            @AuthenticationPrincipal User currentUser) {
        try {
            List<UserDTO> participants = customPlaceService.getParticipants(placeId, currentUser);
            return ResponseEntity.ok(participants);
        } catch (SecurityException e) {
            // Wenn der User nicht der Ersteller ist -> 403 Forbidden
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            // Wenn der Place nicht gefunden wird -> 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }
}