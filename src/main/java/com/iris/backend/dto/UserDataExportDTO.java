package com.iris.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for exporting user data.
 *
 * This record encapsulates key details of a user, including their unique identifier,
 * username, email, photos they have uploaded, and their friends. It is commonly used
 * in scenarios involving user data export or serialization.
 *
 * Fields:
 * - userId: A UUID representing the unique identifier of the user.
 * - username: The username of the user.
 * - email: The email address of the user.
 * - uploadedPhotos: A list of exported photos associated with the user.
 * - friends: A list of exported friendships representing the user's friends.
 */
public record UserDataExportDTO(
        UUID userId,
        String username,
        String email,
        List<ExportedPhotoDTO> uploadedPhotos,
        List<ExportedFriendshipDTO> friends
) {}