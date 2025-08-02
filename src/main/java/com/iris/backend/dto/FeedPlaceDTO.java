package com.iris.backend.dto;

import java.time.OffsetDateTime;

/**
 * Represents a data transfer object (DTO) for a specific place.
 *
 * This record is used to encapsulate details about a place, such as its
 * unique internal identifier, its associated Google Place ID, name, and
 * address. It is typically used in contexts requiring location-based
 * information or interactions with location-related services.
 *
 * Fields:
 * - id: The unique internal identifier of the place.
 * - googlePlaceId: The Google Place ID associated with the place.
 * - name: The name of the place.
 * - address: The address of the place.
 */
public record FeedPlaceDTO(
        Long id,
        String googlePlaceId,
        String name,
        String coverImageUrl,
        OffsetDateTime coverImageDate,
        OffsetDateTime newestDate,
        long photoCount,
        String address
) {}
