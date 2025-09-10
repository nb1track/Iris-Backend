package com.iris.backend.dto;

import com.iris.backend.model.enums.PlaceAccessType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateCustomPlaceRequestDTO(
        @NotBlank String name,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull @Min(10) Integer radiusMeters,
        @NotNull PlaceAccessType accessType,
        String accessKey, // Optional, für Passwort/QR
        @NotNull Boolean isTrending,
        @NotNull Boolean isLive,
        OffsetDateTime scheduledLiveAt, // Optional
        @NotNull @Future OffsetDateTime expiresAt,
        Boolean challengesActivated
) {}