package com.chaptime.backend.dto;

import com.chaptime.backend.model.enums.PhotoVisibility;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for a photo upload request.
 *
 * This record encapsulates the essential details required when uploading
 * a photo. It includes information about the location where the photo
 * was taken, its visibility setting, and the associated place ID.
 *
 * Fields:
 * - latitude: The latitude of the location where the photo was taken.
 * - longitude: The longitude of the location where the photo was taken.
 * - visibility: The visibility status of the photo (e.g., PUBLIC, FRIENDS).
 * - placeId: The internal identifier of the place associated with the photo.
 *
 * Constraints:
 * - All fields are required and must not be null.
 */
public record PhotoUploadRequest(
        @NotNull Double latitude, // Die genauen Koordinaten des Fotos
        @NotNull Double longitude,
        @NotNull PhotoVisibility visibility,
        @NotNull Long placeId,// Unsere interne ID für den ausgewählten Ort
        List<UUID>friendIds
) {}