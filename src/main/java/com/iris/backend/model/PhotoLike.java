package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "photo_likes")
@Getter
@Setter
public class PhotoLike {

    @EmbeddedId
    private PhotoLikeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("photoId")
    private Photo photo;

    @Column(name = "liked_at", nullable = false)
    private OffsetDateTime likedAt;

    @PrePersist
    protected void onLike() {
        likedAt = OffsetDateTime.now();
    }
}