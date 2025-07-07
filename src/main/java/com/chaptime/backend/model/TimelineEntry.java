package com.chaptime.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

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