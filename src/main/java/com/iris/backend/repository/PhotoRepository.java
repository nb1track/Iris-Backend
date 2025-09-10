package com.iris.backend.repository;

import com.iris.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import java.time.OffsetDateTime;
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


    @Query(value = """
    SELECT DISTINCT ph.*
    FROM
        photos ph,
        jsonb_to_recordset(cast(:historyJson as jsonb)) AS h(latitude float, longitude float, "timestamp" timestamptz)
    WHERE
        ph.place_id = :placeId
        AND ph.visibility = 'PUBLIC'
        -- Prüfe, ob der historische Punkt des Users in der Nähe des Ortes war (z.B. 500m Radius)
        AND ST_DWithin(
            (SELECT location FROM googlePlaces WHERE id = :placeId),
            ST_MakePoint(h.longitude, h.latitude)::geography,
            500
        )
        -- Prüfe, ob das Foto im 5-Stunden-Fenster vor diesem Besuch hochgeladen wurde
        AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
    ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
    List<Photo> findPhotosForPlaceMatchingHistoricalBatch(
            @Param("placeId") Long placeId,
            @Param("historyJson") String historyJson
    );

    List<Photo> findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
            List<User> uploaders,
            PhotoVisibility visibility,
            OffsetDateTime currentTime
    );

    List<Photo> findAllByUploader(User uploader);


}