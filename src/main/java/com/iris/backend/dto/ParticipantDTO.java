package com.iris.backend.dto;

import java.util.UUID;

/**
 * Ein spezielles DTO für Listen von Teilnehmern (z.B. in Custom Places),
 * das zusätzlich den Freundschaftsstatus zum anfragenden User enthält.
 */
public record ParticipantDTO(
        UUID id,
        String username,
        String profileImageUrl,
        boolean isFriend // Spezifisches Feld nur für diesen Anwendungsfall
) {}