package com.chaptime.backend.dto;

import com.chaptime.backend.model.enums.PhotoVisibility;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A data transfer object (DTO) representing an exported photo.
 * This record encapsulates detailed information about a photo including its
 * unique identifier, storage location, visibility, upload timestamp, and geolocation.
 * It is typically used in contexts where user data is exported or serialized.
 *
 * Fields:
 * - photoId: A UUID representing the unique identifier of the photo.
 * - storageUrl: The storage URL where the photo is hosted.
 * - visibility: The visibility status of the photo (e.g., PUBLIC, FRIENDS).
 * - uploadedAt: The timestamp indicating when the photo was uploaded.
 * - latitude: The geographical latitude of where the photo was taken.
 * - longitude: The geographical longitude of where the photo was taken.
 */
public record ExportedPhotoDTO(
        UUID photoId,
        String storageUrl,
        PhotoVisibility visibility,
        OffsetDateTime uploadedAt,
        double latitude,
        double longitude
) {}