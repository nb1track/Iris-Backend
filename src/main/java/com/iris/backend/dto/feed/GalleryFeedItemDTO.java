package com.iris.backend.dto.feed;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dies ist das vereinheitlichte DTO für JEDE Galerie im "Entdecken"-Feed.
 * Es fasst Google Places und Custom Places zusammen.
 * Was nicht zutrifft, ist null.
 */
public record GalleryFeedItemDTO(
        // --- Gemeinsame Felder ---
        GalleryPlaceType placeType, // WICHTIG: GOOGLE_POI oder IRIS_SPOT
        String name,
        double latitude,
        double longitude,

        // --- Aggregierte Foto-Infos (aus der Query) ---
        String coverImageUrl,
        long photoCount,
        OffsetDateTime newestPhotoTimestamp,

        // --- ID-Felder (nur eines ist gesetzt) ---
        Long googlePlaceId,    // Wenn placeType = GOOGLE_POI
        UUID customPlaceId,  // Wenn placeType = IRIS_SPOT

        // --- Felder nur für GOOGLE_POI ---
        String address,

        // --- Felder nur für IRIS_SPOT ---
        Integer radiusMeters,
        String accessType, // z.B. "PUBLIC", "PASSWORD"
        Boolean isTrending,
        Boolean isLive,
        OffsetDateTime expiresAt
) {}