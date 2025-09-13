package com.iris.backend.controller;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.PlaceDTO;
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

    @PostMapping
    public ResponseEntity<UUID> createCustomPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestBody CreateCustomPlaceRequestDTO request
    ) {
        CustomPlace newPlace = customPlaceService.createCustomPlace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlace.getId());
    }

    @GetMapping("/my-spots")
    public ResponseEntity<List<CustomPlace>> getMyCreatedSpots(@AuthenticationPrincipal User currentUser) {
        // Hier wird die neue Repository-Methode aufgerufen
        List<CustomPlace> mySpots = customPlaceRepository.findAllByCreatorOrderByCreatedAtDesc(currentUser);
        return ResponseEntity.ok(mySpots);
    }

    @GetMapping("/trending")
    public ResponseEntity<List<PlaceDTO>> getTrendingSpots() {
        List<PlaceDTO> trendingSpots = placeService.findTrendingSpots(); // Diese Methode erstellen wir jetzt
        return ResponseEntity.ok(trendingSpots);
    }
}