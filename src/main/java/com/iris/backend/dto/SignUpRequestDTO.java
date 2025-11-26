package com.iris.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents the payload for a user signup request.
 * Matches the structure defined in signup.md.
 */
public record SignUpRequestDTO(
        @NotBlank String username,
        @NotBlank String firstname,
        @NotBlank String lastname,
        String base64Image, // Optional, falls der User kein Bild hochlädt
        String phoneNumber
) {}