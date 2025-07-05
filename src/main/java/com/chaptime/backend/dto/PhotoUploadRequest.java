package com.chaptime.backend.dto;

import com.chaptime.backend.model.enums.PhotoVisibility;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Ein Java Record ist eine moderne, kompakte Art, eine reine Datenklasse zu erstellen.
public record PhotoUploadRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull PhotoVisibility visibility,
        String placeId, // optional
        @NotNull UUID userId // TEMPORÄR für Testzwecke
) {}