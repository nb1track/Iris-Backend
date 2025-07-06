package com.chaptime.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Represents a data transfer object (DTO) for updating a user's location.
 *
 * This DTO is designed to encapsulate the necessary geographical information
 * for updating a user's location within the system. It ensures that the latitude
 * and longitude values are always provided and are not null.
 *
 * Fields:
 * - latitude: The geographical latitude of the location to be updated.
 * - longitude: The geographical longitude of the location to be updated.
 *
 * Constraints:
 * - Both latitude and longitude fields are required and must not be null.
 */
public record LocationUpdateRequestDTO(
        @NotNull Double latitude,
        @NotNull Double longitude
) {}