package com.iris.backend.repository;

import com.iris.backend.model.CustomPlace;
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


    /**
     * HIER IST DIE KORREKTUR:
     * Alle Parameter sind jetzt konsequent nummeriert (?1, ?2).
     * Die @Param-Annotationen wurden entfernt, da sie nicht mehr benötigt werden.
     */
    @Query(value = """
WITH historical_points AS (
    SELECT
        (h ->> 'latitude')::float AS latitude,
        (h ->> 'longitude')::float AS longitude,
        (h ->> 'timestamp')::timestamptz AS "timestamp"
    FROM
        jsonb_array_elements(?2::jsonb) AS h
)
SELECT DISTINCT ph.*
FROM
    photos ph
JOIN
    historical_points h ON ST_DWithin(
        (SELECT location FROM google_places WHERE id = ?1),
        ST_MakePoint(h.longitude, h.latitude)::geography,
        500
    )
WHERE
    ph.google_place_id = ?1
    AND ph.visibility = 'PUBLIC'
    AND ph.uploaded_at BETWEEN (h."timestamp" - interval '5 hours') AND h."timestamp"
ORDER BY ph.uploaded_at DESC
""", nativeQuery = true)
    List<Photo> findPhotosForPlaceMatchingHistoricalBatch(
            Long placeId,
            String historyJson
    );


    List<Photo> findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
            List<User> uploaders,
            PhotoVisibility visibility,
            OffsetDateTime currentTime
    );

    List<Photo> findAllByUploader(User uploader);

    /**
     * Findet alle einzigartigen (distinct) Benutzer, die Fotos zu einem
     * bestimmten CustomPlace hochgeladen haben.
     * @param customPlace Der CustomPlace, für den die Teilnehmer gesucht werden.
     * @return Eine Liste von einzigartigen User-Objekten.
     */
    @Query("SELECT DISTINCT p.uploader FROM Photo p WHERE p.customPlace = :customPlace")
    List<User> findDistinctUploadersByCustomPlace(CustomPlace customPlace);

}