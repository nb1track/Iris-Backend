package com.iris.backend.dto;

import com.iris.backend.dto.feed.GalleryPlaceType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RankingItemDTO(
        int rank,
        UUID photoId,
        String storageUrl,
        OffsetDateTime timestamp,
        GalleryPlaceType placeType,
        Long googlePlaceId,
        UUID customPlaceId,
        String placeName,
        UUID userId,
        String username,
        String profileImageUrl,
        int likeCount
) {}