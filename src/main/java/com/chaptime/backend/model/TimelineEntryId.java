package com.chaptime.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

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