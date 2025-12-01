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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    // --- GOOGLE PLACES ---

    /**
     * 1. FÜR "PICTURES FROM OTHERS":
     * Findet öffentliche Fotos ANDERER Nutzer (excludeUserId).
     */
    @Query(value = """
WITH historical_points AS (
    SELECT
        (h ->> 'latitude')::float AS latitude,
        (h ->> 'longitude')::float AS longitude,
        (h ->> 'timestamp')::timestamptz AS "timestamp"
    FROM
        jsonb_array_elements(CAST(:historyJson AS jsonb)) AS h
)
SELECT DISTINCT ph.*
FROM
    photos ph
JOIN
    historical_points h ON ST_DWithin(
        (SELECT location FROM google_places WHERE id = :googlePlaceId),
        ST_MakePoint(h.longitude, h.latitude)::geography,
        500
    )
WHERE
    ph.google_place_id = :googlePlaceId
    AND (ph.visibility = 'PUBLIC' OR ph.visibility = 'VISIBLE_TO_ALL')
    AND ph.uploader_id != :excludeUserId  -- WICHTIG: Eigene Fotos ausschließen
    AND ph.uploaded_at BETWEEN (h."timestamp" - interval '5 hours') AND h."timestamp"
ORDER BY ph.uploaded_at DESC
""", nativeQuery = true)
    List<Photo> findPhotosForGooglePlaceMatchingHistoricalBatchFromOthers(
            @Param("googlePlaceId") Long googlePlaceId,
            @Param("historyJson") String historyJson,
            @Param("excludeUserId") UUID excludeUserId
    );

    /**
     * 2. FÜR "YOUR SHARED PHOTOS":
     * Findet NUR Fotos des aktuellen Nutzers (targetUserId).
     * Hier ist die Visibility egal, da es meine eigenen sind.
     */
    @Query(value = """
WITH historical_points AS (
    SELECT
        (h ->> 'latitude')::float AS latitude,
        (h ->> 'longitude')::float AS longitude,
        (h ->> 'timestamp')::timestamptz AS "timestamp"
    FROM
        jsonb_array_elements(CAST(:historyJson AS jsonb)) AS h
)
SELECT DISTINCT ph.*
FROM
    photos ph
JOIN
    historical_points h ON ST_DWithin(
        (SELECT location FROM google_places WHERE id = :googlePlaceId),
        ST_MakePoint(h.longitude, h.latitude)::geography,
        500
    )
WHERE
    ph.google_place_id = :googlePlaceId
    AND ph.uploader_id = :targetUserId -- WICHTIG: Nur meine Fotos
    AND ph.uploaded_at BETWEEN (h."timestamp" - interval '5 hours') AND h."timestamp"
ORDER BY ph.uploaded_at DESC
""", nativeQuery = true)
    List<Photo> findPhotosForGooglePlaceMatchingHistoricalBatchFromUser(
            @Param("googlePlaceId") Long googlePlaceId,
            @Param("historyJson") String historyJson,
            @Param("targetUserId") UUID targetUserId
    );


    // --- CUSTOM PLACES ---

    /**
     * 3. FÜR "PICTURES FROM OTHERS" (Custom Place):
     * KORRIGIERT: Benannte Parameter statt ?1, ?2
     */
    @Query(value = """
        WITH historical_points AS (
            SELECT
                (h ->> 'latitude')::float AS latitude,
                (h ->> 'longitude')::float AS longitude,
                (h ->> 'timestamp')::timestamptz AS "timestamp"
            FROM
                jsonb_array_elements(CAST(:historyJson AS jsonb)) AS h
        )
        SELECT DISTINCT ph.*
        FROM
            photos ph
        JOIN
            custom_places cp ON ph.custom_place_id = cp.id
        JOIN
            historical_points h ON ST_DWithin(
                cp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                cp.radius_meters
            )
        WHERE
            ph.custom_place_id = :customPlaceId
            AND (ph.visibility = 'PUBLIC' OR ph.visibility = 'VISIBLE_TO_ALL')
            AND ph.uploader_id != :excludeUserId
            AND ph.uploaded_at BETWEEN (h."timestamp" - interval '5 hours') AND h."timestamp"
        ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
    List<Photo> findPhotosForCustomPlaceMatchingHistoricalBatchFromOthers(
            @Param("customPlaceId") UUID customPlaceId,
            @Param("historyJson") String historyJson,
            @Param("excludeUserId") UUID excludeUserId
    );

    /**
     * 4. FÜR "YOUR SHARED PHOTOS" (Custom Place):
     * KORRIGIERT: Benannte Parameter statt ?1, ?2
     */
    @Query(value = """
        WITH historical_points AS (
            SELECT
                (h ->> 'latitude')::float AS latitude,
                (h ->> 'longitude')::float AS longitude,
                (h ->> 'timestamp')::timestamptz AS "timestamp"
            FROM
                jsonb_array_elements(CAST(:historyJson AS jsonb)) AS h
        )
        SELECT DISTINCT ph.*
        FROM
            photos ph
        JOIN
            custom_places cp ON ph.custom_place_id = cp.id
        JOIN
            historical_points h ON ST_DWithin(
                cp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                cp.radius_meters
            )
        WHERE
            ph.custom_place_id = :customPlaceId
            AND ph.uploader_id = :targetUserId
            AND ph.uploaded_at BETWEEN (h."timestamp" - interval '5 hours') AND h."timestamp"
        ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
    List<Photo> findPhotosForCustomPlaceMatchingHistoricalBatchFromUser(
            @Param("customPlaceId") UUID customPlaceId,
            @Param("historyJson") String historyJson,
            @Param("targetUserId") UUID targetUserId
    );

    /**
     * Findet alle Fotos für den Freunde-Feed.
     * Das beinhaltet Fotos mit Visibility 'FRIENDS' UND 'VISIBLE_TO_ALL' (Hybrid).
     * Fotos mit Visibility 'PUBLIC' (was jetzt "Nur Spot" bedeutet) werden hier NICHT geladen.
     */
    @Query("SELECT p FROM Photo p WHERE p.uploader IN :friends AND (p.visibility = 'FRIENDS' OR p.visibility = 'VISIBLE_TO_ALL') AND p.expiresAt > :now ORDER BY p.uploadedAt DESC")
    List<Photo> findFriendsFeedPhotos(@Param("friends") List<User> friends, @Param("now") OffsetDateTime now);

    List<Photo> findAllByUploader(User uploader);

    @Query("SELECT DISTINCT p.uploader FROM Photo p WHERE p.customPlace = :customPlace")
    List<User> findDistinctUploadersByCustomPlace(CustomPlace customPlace);

    @Query("SELECT DISTINCT p.uploader FROM Photo p WHERE p.googlePlace.id = :googlePlaceId")
    List<User> findDistinctUploadersByGooglePlaceId(@Param("googlePlaceId") Long googlePlaceId);
    Optional<Photo> findFirstByGooglePlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(Long googlePlaceId, PhotoVisibility visibility, OffsetDateTime now);
    long countByGooglePlaceIdAndVisibilityAndExpiresAtAfter(Long googlePlaceId, PhotoVisibility visibility, OffsetDateTime now);
    Optional<Photo> findFirstByCustomPlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(UUID customPlaceId, PhotoVisibility visibility, OffsetDateTime now);
    long countByCustomPlaceIdAndVisibilityAndExpiresAtAfter(UUID customPlaceId, PhotoVisibility visibility, OffsetDateTime now);
}