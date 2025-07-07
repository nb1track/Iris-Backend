package com.chaptime.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the composite primary key for a TimelineEntry entity.
 *
 * This class acts as an embedded composite key that consists of two fields:
 * - `userId`: The unique identifier of the user associated with the timeline entry.
 * - `photoId`: The unique identifier of the photo associated with the timeline entry.
 *
 * The combination of `userId` and `photoId` ensures a unique timeline entry,
 * allowing a specific photo to be linked to a user as part of their timeline.
 *
 * This class implements `Serializable` and overrides the `equals()` and `hashCode()`
 * methods, which are necessary for entities with composite keys.
 *
 * The annotations configure the key fields:
 * - `@Embeddable`: Marks this class as a composite key definition.
 * - `@Column`: Specifies the mapping of the fields to database columns.
 */
@Embeddable
public class TimelineEntryId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "photo_id")
    private UUID photoId;

    // equals() und hashCode() sind für Composite Keys zwingend erforderlich
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimelineEntryId that = (TimelineEntryId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(photoId, that.photoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, photoId);
    }

    // Getter und Setter...
}