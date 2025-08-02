package com.iris.backend.repository;

import com.iris.backend.dto.FeedPlaceDTO;
import com.iris.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeedRepository extends JpaRepository<Photo, UUID> {

    @Query(value = """
            WITH history AS (
                SELECT *
                FROM jsonb_to_recordset(CAST(:historyJson AS jsonb))
                    AS h(latitude float, longitude float, "timestamp" timestamptz)
            ), matching_photos AS (
                 SELECT ph.*, h.timestamp AS visit_time
                 FROM history h
                 JOIN photos ph
                     ON ph.visibility = 'PUBLIC'
                    AND ph.uploaded_at >= now() - interval '24 hours'
                  AND ph.uploaded_at BETWEEN h.timestamp - interval '5 hours' AND h.timestamp + interval '5 hours'
                 JOIN places p
                   ON ph.place_id = p.id
                  AND ST_DWithin(p.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
             )
            SELECT
                p.id AS id,
                p.google_place_id AS googlePlaceId,
                p.name AS name,
                p.address AS address,
            
                -- cover image
                (SELECT ph1.storage_url FROM photos ph1
                 WHERE ph1.place_id = p.id AND ph1.visibility = 'PUBLIC'
                 ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageUrl,
            
                (SELECT ph1.uploaded_at FROM photos ph1
                 WHERE ph1.place_id = p.id AND ph1.visibility = 'PUBLIC'
                 ORDER BY ph1.uploaded_at ASC LIMIT 1) AS coverImageDate,
            
                MAX(mp.uploaded_at) AS newestDate,
                COUNT(mp.id) AS photoCount,
                MIN(mp.visit_time) AS visitTime
            
            FROM matching_photos mp
            JOIN places p ON mp.place_id = p.id
            
            GROUP BY p.id, p.google_place_id, p.name, p.address
            ORDER BY visitTime DESC    
            """, nativeQuery = true)
    List<Object[]> findPlacesWithPhotosMatchingUserHistory(
            @Param("historyJson") String historyJson,
            @Param("radiusInMeters") double radiusInMeters
    );
}