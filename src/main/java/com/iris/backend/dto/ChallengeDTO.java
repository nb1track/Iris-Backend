package com.iris.backend.dto;

import com.iris.backend.dto.UserDTO; // Wiederverwenden des existierenden DTOs
import java.util.List;
import java.util.UUID;

public record ChallengeDTO(
        UUID id, // Die ID der CustomPlaceChallenge-Instanz
        String name,
        int progress, // Die berechneten 0-100%
        String icon, // Der icon_key
        boolean joined,
        List<UserDTO> participants
) {}