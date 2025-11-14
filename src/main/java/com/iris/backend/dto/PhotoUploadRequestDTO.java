package com.iris.backend.dto;

import com.iris.backend.model.enums.PhotoVisibility;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PhotoUploadRequestDTO(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull PhotoVisibility visibility,
        Long googlePlaceId, // Kann null sein
        UUID customPlaceId,   // Kann null sein
        List<UUID> friendIds, // Für das direkte Teilen mit Freunden
        UUID challengeId
) {}