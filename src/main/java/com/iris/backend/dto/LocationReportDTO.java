package com.iris.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO, das von einem Freundesgerät (z. B. User B) an das Backend gesendet wird,
 * als Antwort auf eine Standortanfrage von User A.
 */
public record LocationReportDTO(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotBlank String targetFcmToken // Das FCM-Token des ursprünglichen Anfragers (User A)
) {}