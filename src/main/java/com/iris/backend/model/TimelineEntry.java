package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

/**
 * Represents an entry in the user's timeline consisting of a specific photo saved by a user.
 *
 * The TimelineEntry entity is mapped to the `timeline_entries` database table.
 * It associates a Photo entity with a User entity to represent a saved photo in the user's timeline.
 *
 * Key Features:
 * - Utilizes a composite primary key represented by the `TimelineEntryId` class.
 * - Links the `user` and `photo` properties to their respective entities through `@ManyToOne` relationships.
 * - Automatically sets the `savedAt` timestamp upon the entity's persistence.
 *
 * Entity Annotations:
 * - `@Entity`: Defines this class as a JPA entity.
 * - `@Table`: Specifies the table name in the database.
 * - `@EmbeddedId`: Specifies that `TimelineEntryId` is the composite key for this entity.
 *
 * Field Annotations:
 * - `@ManyToOne`: Defines the relationships to the User and Photo entities.
 * - `@MapsId`: Maps the composite key fields to specific entity fields (userId, photoId).
 * - `@JoinColumn`: Specifies the foreign key constraints.
 * - `@Column`: Configures the `savedAt` timestamp column.
 *
 * Lifecycle Callbacks:
 * - `@PrePersist`: Sets the `savedAt` timestamp to the current time when the entity is persisted.
 */
@Entity
@Table(name = "timeline_entries")
@Getter
@Setter
public class TimelineEntry {

    @EmbeddedId
    private TimelineEntryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId") // Verknüpft das 'user'-Feld mit dem 'userId'-Feld im EmbeddedId
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("photoId") // Verknüpft das 'photo'-Feld mit dem 'photoId'-Feld im EmbeddedId
    @JoinColumn(name = "photo_id")
    private Photo photo;

    @Column(name = "saved_at", nullable = false)
    private OffsetDateTime savedAt;

    public TimelineEntry() {
        this.id = new TimelineEntryId();
    }

    @PrePersist
    protected void onSave() {
        savedAt = OffsetDateTime.now();
    }
}