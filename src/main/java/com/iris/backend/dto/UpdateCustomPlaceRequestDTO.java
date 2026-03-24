package com.iris.backend.dto;

import com.iris.backend.model.enums.PlaceAccessType;
import java.time.OffsetDateTime;

public record UpdateCustomPlaceRequestDTO(
        String name,
        Integer radiusMeters,
        PlaceAccessType accessType,
        String accessKey,
        Boolean isTrending,
        Boolean isLive,
        OffsetDateTime scheduledLiveAt,
        OffsetDateTime expiresAt,
        Boolean challengesActivated
) {}