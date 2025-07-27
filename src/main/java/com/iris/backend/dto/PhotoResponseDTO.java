package com.iris.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for responding with photo details.
 *
 * This DTO is used to encapsulate the essential information about a photo
 * that is being returned from API calls or service layers. It includes details
 * about the photo's unique identifier, its storage location, and the username
 * of the uploader.
 *
 * Fields:
 * - photoId: A UUID identifying the photo uniquely.
 * - storageUrl: The URL where the photo is stored and can be accessed.
 * - uploaderUsername: The username of the user who uploaded the photo.
 */
public record PhotoResponseDTO(
        UUID photoId,
        String storageUrl,
        OffsetDateTime timestamp,
        int placeId,
        String placeName,
        UUID userId,
        String username
) {}