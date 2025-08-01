package com.iris.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Dieses DTO enthält alle Daten, die zum Erstellen eines neuen Ortes benötigt werden.
public record CreatePlaceRequestDTO(
        @NotBlank(message = "Place name cannot be empty")
        String name,

        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude
) {}