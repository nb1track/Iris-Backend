package com.iris.backend.repository;

import com.iris.backend.model.PhotoLike;
import com.iris.backend.model.PhotoLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PhotoLikeRepository extends JpaRepository<PhotoLike, PhotoLikeId> {
    // Zählt, wie viele Likes ein bestimmtes Foto hat.
    int countByIdPhotoId(UUID photoId);
}