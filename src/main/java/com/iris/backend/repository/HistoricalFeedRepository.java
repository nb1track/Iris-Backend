package com.iris.backend.repository;

import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType; // Importiere dein Enum
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Wir nutzen Photo als "Basis-Repository", da JpaRepository ein Entity braucht,
// aber die Magie passiert in der @Query.
@Repository
public interface HistoricalFeedRepository extends JpaRepository<com.iris.backend.model.Photo, UUID> {

    /**
     * Dies ist die neue, vereinheitlichte native Query für den Historical Feed.
     * Sie nutzt UNION ALL, um GooglePlaces und CustomPlaces zu kombinieren
     * und gibt direkt die Spalten zurück, die wir für GalleryFeedItemDTO brauchen.
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
                NULL::timestamptz AS expires_at
            FROM photos p
            JOIN google_places gp ON p.google_place_id = gp.id
            JOIN historical_points h ON ST_DWithin(
                gp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                gp.radius_meters -- Nutzt den individuellen Radius des POI
            )
            WHERE p.visibility = 'PUBLIC'
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
                cp.expires_at
            FROM photos p
            JOIN custom_places cp ON p.custom_place_id = cp.id
            JOIN historical_points h ON ST_DWithin(
                cp.location,
                ST_MakePoint(h.longitude, h.latitude)::geography,
                cp.radius_meters -- Nutzt den individuellen Radius des Spots
            )
            WHERE p.visibility = 'PUBLIC'
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
                COUNT(photo_id) AS photo_count,
                -- Finde die URL des neusten Fotos
                (ARRAY_AGG(storage_url ORDER BY uploaded_at DESC))[1] AS cover_image_url,
                MAX(uploaded_at) AS newest_photo_timestamp
            FROM all_photos
            GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
        )
        
        -- 5. Finale Selektion als Interface-Projektion
        -- Spring Data wird diese Spalten automatisch auf das GalleryFeedItemDTO mappen
        SELECT
            place_type AS placeType,
            name,
            latitude,
            longitude,
            cover_image_url AS coverImageUrl,
            photo_count AS photoCount,
            newest_photo_timestamp AS newestPhotoTimestamp,
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

    /**
     * Interface-Projektion, damit Spring Data die nativen Query-Ergebnisse
     * direkt auf unser GalleryFeedItemDTO mappen kann.
     * Die Methodennamen (get...) MÜSSEN exakt den Spalten-Aliassen (AS ...)
     * aus der SQL-Query entsprechen.
     */
    interface GalleryFeedItemDTOProjection {
        GalleryPlaceType getPlaceType();
        String getName();
        double getLatitude();
        double getLongitude();
        String getCoverImageUrl();
        long getPhotoCount();
        OffsetDateTime getNewestPhotoTimestamp();
        Long getGooglePlaceId();
        UUID getCustomPlaceId();
        String getAddress();
        Integer getRadiusMeters();
        String getAccessType();
        boolean getIsTrending();
        boolean getIsLive();
        OffsetDateTime getExpiresAt();

        // Standard-Methode, um die Projektion einfach in das echte DTO umzuwandeln
        default GalleryFeedItemDTO toDTO() {
            return new GalleryFeedItemDTO(
                    getPlaceType(),
                    getName(),
                    getLatitude(),
                    getLongitude(),
                    getCoverImageUrl(),
                    getPhotoCount(),
                    getNewestPhotoTimestamp(),
                    getGooglePlaceId(),
                    getCustomPlaceId(),
                    getAddress(),
                    getRadiusMeters(),
                    getAccessType(),
                    getIsTrending(),
                    getIsLive(),
                    getExpiresAt()
            );
        }
    }
}