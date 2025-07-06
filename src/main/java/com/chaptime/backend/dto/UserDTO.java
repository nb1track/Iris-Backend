package com.chaptime.backend.dto;

import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for a user.
 *
 * This record is designed to encapsulate core user details,
 * including the unique identifier of the user and their username.
 * It is typically used in situations requiring a streamlined representation
 * of user information, such as interactions with external systems or APIs.
 *
 * Fields:
 * - id: A UUID representing the unique identifier of the user.
 * - username: The username associated with the user.
 */
public record UserDTO(UUID id, String username) {}