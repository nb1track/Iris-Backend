package com.iris.backend.dto;

/**
 * Represents a data transfer object (DTO) for a user signup request.
 *
 * This DTO encapsulates the necessary details required for a user
 * to register or sign up within the system. Specifically, it includes
 * the username chosen by the user during the signup process.
 *
 * Fields:
 * - username: The unique username selected by the user for their account.
 */
public record SignUpRequestDTO(String username) {}