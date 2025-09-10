package com.iris.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PhotoLikeId implements Serializable {
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "photo_id")
    private UUID photoId;

    public PhotoLikeId() {
    }

    public PhotoLikeId(UUID userId, UUID photoId) {
        this.userId = userId;
        this.photoId = photoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhotoLikeId that = (PhotoLikeId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(photoId, that.photoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, photoId);
    }
}