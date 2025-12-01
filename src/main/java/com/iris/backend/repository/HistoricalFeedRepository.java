package com.iris.backend.repository;

import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HistoricalFeedRepository extends JpaRepository<com.iris.backend.model.Photo, UUID> {

    /**
     * Dies ist die neue, vereinheitlichte native Query für den Historical Feed.
     * UPDATE: Priorisiert jetzt das `cover_image_url` aus `custom_places`, falls vorhanden.
     */
    @Query(value = """
        WITH historical_points AS (
            -- Wandelt den JSON-Input in eine Tabelle um
            SELECT
                (value ->> 'latitude')::float AS latitude,
                (value ->> 'longitude')::float AS longitude,
                (value ->> 'timestamp')::timestamptz AS timestamp
            FROM jsonb_array_elements(CAST(:historyJson AS jsonb))
        ),
        
        -- 1. Finde alle passenden Google POI Fotos
        google_photos AS (
            SELECT DISTINCT
                p.id AS photo_id,
                p.storage_url,
                p.uploaded_at,
                gp.id::text AS place_id,
                gp.name,
                gp.address,
                ST_Y(gp.location::geometry) AS latitude,
                ST_X(gp.location::geometry) AS longitude,
                'GOOGLE_POI' AS place_type,
                gp.radius_meters,
                NULL::text AS access_type,
                FALSE AS is_trending,
                TRUE AS is_live,
                NULL::timestamptz AS expires_at,
                NULL::text AS custom_cover_image -- Google Places haben kein Custom Cover
            FROM photos p
            JOIN google_places gp ON p.google_place_id = gp.id
            JOIN historical_points h ON ST_DWithin(
                gp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                gp.radius_meters
            )
            WHERE (p.visibility = 'PUBLIC' OR p.visibility = 'VISIBLE_TO_ALL')
              AND p.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        ),
        
        -- 2. Finde alle passenden Custom Place Fotos
        custom_photos AS (
            SELECT DISTINCT
                p.id AS photo_id,
                p.storage_url,
                p.uploaded_at,
                cp.id::text AS place_id,
                cp.name,
                NULL::text AS address,
                ST_Y(cp.location::geometry) AS latitude,
                ST_X(cp.location::geometry) AS longitude,
                'IRIS_SPOT' AS place_type,
                cp.radius_meters,
                cp.access_type,
                cp.is_trending,
                cp.is_live,
                cp.expires_at,
                cp.cover_image_url AS custom_cover_image -- Hole das Cover-Bild aus dem Custom Place
            FROM photos p
            JOIN custom_places cp ON p.custom_place_id = cp.id
            JOIN historical_points h ON ST_DWithin(
                cp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                cp.radius_meters
            )
            WHERE (p.visibility = 'PUBLIC' OR p.visibility = 'VISIBLE_TO_ALL')
              AND p.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        ),
        
        -- 3. Kombiniere beide Foto-Listen
        all_photos AS (
            SELECT * FROM google_photos
            UNION ALL
            SELECT * FROM custom_photos
        ),
        
        -- 4. Gruppiere die Fotos nach Ort und hole die Cover-Infos
        grouped_places AS (
            SELECT
                place_id,
                place_type,
                name,
                address,
                latitude,
                longitude,
                COALESCE(radius_meters, 0) AS radius_meters,
                access_type,
                is_trending,
                is_live,
                expires_at,
                custom_cover_image, -- Müssen wir mitgruppieren
                COUNT(photo_id) AS photo_count,
                -- Finde die URL des neusten USER-Fotos als Fallback
                (ARRAY_AGG(storage_url ORDER BY uploaded_at DESC))[1] AS latest_user_photo,
                MAX(uploaded_at) AS newest_photo_timestamp
            FROM all_photos
            GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
        )
        
        -- 5. Finale Selektion als Interface-Projektion
        SELECT
            place_type AS placeType,
            name,
            latitude,
            longitude,
            -- HIER IST DIE ÄNDERUNG: Priorisiere das Custom Cover Image
            COALESCE(custom_cover_image, latest_user_photo) AS coverImageUrl,
            photo_count AS photoCount,
            newest_photo_timestamp::timestamptz AS newestPhotoTimestamp,
            CASE WHEN place_type = 'GOOGLE_POI' THEN place_id::bigint ELSE NULL END AS googlePlaceId,
            CASE WHEN place_type = 'IRIS_SPOT' THEN place_id::uuid ELSE NULL END AS customPlaceId,
            address,
            radius_meters AS radiusMeters,
            access_type AS accessType,
            is_trending AS isTrending,
            is_live AS isLive,
            expires_at::timestamptz AS expiresAt
        FROM grouped_places
        ORDER BY newest_photo_timestamp DESC
    """, nativeQuery = true)
    List<GalleryFeedItemDTOProjection> findHistoricalFeed(
            @Param("historyJson") String historyJson
    );

    interface GalleryFeedItemDTOProjection {
        GalleryPlaceType getPlaceType();
        String getName();
        double getLatitude();
        double getLongitude();
        String getCoverImageUrl();
        long getPhotoCount();
        java.time.Instant getNewestPhotoTimestamp();
        Long getGooglePlaceId();
        UUID getCustomPlaceId();
        String getAddress();
        Integer getRadiusMeters();
        String getAccessType();
        boolean getIsTrending();
        boolean getIsLive();
        java.time.Instant getExpiresAt();

        default GalleryFeedItemDTO toDTO() {
            return null;
        }
    }
}