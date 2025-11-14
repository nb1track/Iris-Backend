package com.iris.backend.dto;

import java.util.List;
import java.util.UUID;

// Entspricht der JSON-Struktur aus challenge_content.md (als Objekt, nicht Array)
public record ChallengeContentDTO(
        UUID id,
        String name,
        int progress,
        String icon,
        boolean joined,
        List<PhotoResponseDTO> images,
        List<RankingItemDTO> ranking
) {}