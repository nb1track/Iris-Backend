package com.chaptime.backend.repository;

import com.chaptime.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User; // Import hinzufügen
import com.chaptime.backend.model.enums.PhotoVisibility; // Import hinzufügen
import java.time.OffsetDateTime; // Import hinzufügen

import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    @Query(value = "SELECT * FROM photos p WHERE p.visibility = 'PUBLIC' AND p.expires_at > NOW() AND ST_DWithin(p.location, ST_MakePoint(:longitude, :latitude)::geography, :radiusInMeters)", nativeQuery = true)
    List<Photo> findPublicPhotosWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInMeters") double radiusInMeters
    );

    List<Photo> findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
            List<User> uploaders,
            PhotoVisibility visibility,
            OffsetDateTime currentTime
    );
}