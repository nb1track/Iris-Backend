package com.iris.backend.dto.feed;

import java.util.List;

/**
 * Dies ist das Top-Level-Objekt, das dein Controller zurückgibt.
 * Es bündelt alle Galerien in die 3 Kategorien, die du wolltest.
 */
public record GalleryFeedResponseDTO(
        List<GalleryFeedItemDTO> discoveredSpots, // Für "Entdeckte Spots"
        List<GalleryFeedItemDTO> trendingSpots,   // Für "Trending Spots"
        List<GalleryFeedItemDTO> createdSpots     // Für "Meine erstellten Spots"
) {}