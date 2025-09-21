package com.iris.backend.repository;

import com.iris.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeedRepository extends JpaRepository<Photo, UUID> {

    /**
     * KORREKTUR: Alle Parameter wurden auf den nummerierten Stil (?1, ?2) umgestellt.
     */
    @Query(value = """
        WITH history AS (
            SELECT * FROM jsonb_to_recordset(CAST(?1 AS jsonb))
                AS h(latitude float, longitude float, "timestamp" timestptz)
        ), matching_photos AS (
             SELECT ph.*, h.timestamp AS visit_time
             FROM history h
             JOIN photos ph ON ph.visibility = 'PUBLIC' AND ph.uploaded_at > (now() - interval '48 hours')
             JOIN google_places p ON ph.google_place_id = p.id
                AND ST_DWithin(p.location, ST_MakePoint(h.longitude, h.latitude)::geography, ?2)
             WHERE ph.uploaded_at BETWEEN h.timestamp - interval '5 hours' AND h.timestamp + interval '5 hours'
        )
        SELECT
            p.id AS id, p.google_place_id AS googlePlaceId, p.name, p.address,
            (SELECT ph1.storage_url FROM photos ph1 WHERE ph1.google_place_id = p.id ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageUrl,
            (SELECT ph1.uploaded_at FROM photos ph1 WHERE ph1.google_place_id = p.id ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageDate,
            MAX(mp.uploaded_at) AS newestDate, COUNT(DISTINCT mp.id) AS photoCount, MIN(mp.visit_time) AS visitTime
        FROM matching_photos mp
        JOIN google_places p ON mp.google_place_id = p.id
        GROUP BY p.id, p.google_place_id, p.name, p.address
        ORDER BY visitTime DESC
        """, nativeQuery = true)
    List<Object[]> findGooglePlacesMatchingHistory(
            String historyJson,      // Wird zu ?1
            double radiusInMeters    // Wird zu ?2
    );

    /**
     * KORREKTUR: Alle Parameter wurden auf den nummerierten Stil (?1, ?2) umgestellt.
     */
    @Query(value = """
        WITH history AS (
            SELECT * FROM jsonb_to_recordset(CAST(?1 AS jsonb))
                AS h(latitude float, longitude float, "timestamp" timestptz)
        ), matching_photos AS (
             SELECT ph.*, h.timestamp AS visit_time
             FROM history h
             JOIN photos ph ON ph.visibility = 'PUBLIC' AND ph.uploaded_at > (now() - interval '48 hours')
             JOIN custom_places p ON ph.custom_place_id = p.id
                AND ST_DWithin(p.location, ST_MakePoint(h.longitude, h.latitude)::geography, ?2)
             WHERE ph.uploaded_at BETWEEN h.timestamp - interval '5 hours' AND h.timestamp + interval '5 hours'
        )
        SELECT
            p.id AS id, p.name,
            (SELECT ph1.storage_url FROM photos ph1 WHERE ph1.custom_place_id = p.id ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageUrl,
            (SELECT ph1.uploaded_at FROM photos ph1 WHERE ph1.custom_place_id = p.id ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageDate,
            MAX(mp.uploaded_at) AS newestDate, COUNT(DISTINCT mp.id) AS photoCount, MIN(mp.visit_time) AS visitTime,
            p.access_type, p.is_trending, p.is_live
        FROM matching_photos mp
        JOIN custom_places p ON mp.custom_place_id = p.id
        GROUP BY p.id, p.name, p.access_type, p.is_trending, p.is_live
        ORDER BY visitTime DESC
        """, nativeQuery = true)
    List<Object[]> findCustomPlacesMatchingHistory(
            String historyJson,      // Wird zu ?1
            double radiusInMeters    // Wird zu ?2
    );
}