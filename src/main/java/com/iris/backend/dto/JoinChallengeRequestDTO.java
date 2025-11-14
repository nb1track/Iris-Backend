package com.iris.backend.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record JoinChallengeRequestDTO(
        @NotNull UUID challengeId
) {}