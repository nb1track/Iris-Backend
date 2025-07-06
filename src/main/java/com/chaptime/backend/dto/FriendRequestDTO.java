package com.chaptime.backend.dto;

import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for a friend request.
 *
 * This DTO is used to encapsulate the essential information required
 * to initiate a friend request between users. It typically includes
 * the unique identifier of the addressee (the user to whom the friend
 * request is being sent).
 *
 * Fields:
 * - addresseeId: The unique identifier of the user who is the recipient
 *   of the friend request.
 */
public record FriendRequestDTO(UUID addresseeId) {}