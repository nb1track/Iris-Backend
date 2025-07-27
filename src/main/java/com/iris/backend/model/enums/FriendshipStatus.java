package com.iris.backend.model.enums;

/**
 * Enum representing the status of a friendship between two users.
 *
 * This enum is used in the context of user relationships to define the current state
 * of a friendship, which can either be a pending request or an accepted connection.
 *
 * - PENDING: Indicates that a friendship request has been sent but not yet accepted.
 * - ACCEPTED: Indicates that the friendship request has been approved and the two users are now friends.
 */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED
}