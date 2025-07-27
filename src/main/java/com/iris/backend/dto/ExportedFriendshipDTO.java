package com.iris.backend.dto;

import java.util.UUID;

/**
 * Represents a simplified data transfer object for an exported friendship.
 * This record is used to encapsulate information about a friend's ID and username.
 * Typically used in user data export scenarios.
 *
 * Fields:
 * - friendId: The unique identifier of the friend.
 * - friendUsername: The username of the friend.
 */
public record ExportedFriendshipDTO(
        UUID friendId,
        String friendUsername
) {}