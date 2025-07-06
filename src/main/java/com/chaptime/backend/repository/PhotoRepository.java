package com.chaptime.backend.repository;

import com.chaptime.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.PhotoVisibility;
import java.time.OffsetDateTime;
import com.chaptime.backend.model.Place;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing Photo entities.
 *
 * This interface provides methods for querying and accessing photo data from the
 * database. It extends JpaRepository to inherit standard CRUD operations and includes
 * additional custom query methods for retrieving photos based on specific criteria.
 *
 * Key functionalities:
 * - Fetch public photos within a specified geographical radius.
 * - Fetch photos uploaded by a list of users, filtered by visibility and expiration criteria.
 * - Retrieve photos associated with a specific place, ordered by upload time.
 * - List all photos uploaded by a specific user.
 */
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

    List<Photo> findAllByPlaceAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
            Place place,
            PhotoVisibility visibility,
            OffsetDateTime currentTime
    );

    List<Photo> findAllByUploader(User uploader);
}