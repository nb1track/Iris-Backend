package com.iris.backend.model.enums;

/**
 * Enum representing the visibility of a photo.
 *
 * This enum is used to specify who can view a photo in the system. The visibility
 * can either be:
 *
 * - PUBLIC: The photo is visible to everyone.
 * - FRIENDS: The photo is visible only to friends of the uploader.
 */
public enum PhotoVisibility {
    PUBLIC,
    FRIENDS
}