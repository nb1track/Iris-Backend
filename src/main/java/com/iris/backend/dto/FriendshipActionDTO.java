package com.iris.backend.dto;

import java.util.UUID;

/**
 * Represents a data transfer object (DTO) for performing actions on a friendship request.
 *
 * This DTO is used in scenarios where an action needs to be performed
 * on an existing friendship request, such as accepting or rejecting it.
 *
 * Fields:
 * - friendshipId: The unique identifier of the friendship request to be acted upon.
 */
// Dieses DTO kann sowohl zum Annehmen als auch zum Ablehnen verwendet werden
public record FriendshipActionDTO(UUID friendshipId) {}