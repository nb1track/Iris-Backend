package com.iris.backend.controller;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.service.CustomPlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custom-places")
public class CustomPlaceController {

    private final CustomPlaceService customPlaceService;

    public CustomPlaceController(CustomPlaceService customPlaceService) {
        this.customPlaceService = customPlaceService;
    }

    @PostMapping
    public ResponseEntity<UUID> createCustomPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestBody CreateCustomPlaceRequestDTO request
    ) {
        CustomPlace newPlace = customPlaceService.createCustomPlace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlace.getId());
    }
}