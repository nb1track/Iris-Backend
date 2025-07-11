package com.chaptime.backend.repository;

import com.chaptime.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedRepository extends JpaRepository<Photo, UUID> {

    @Query(value = """
        SELECT DISTINCT ph.*
        FROM
            photos ph,
            -- Wir benutzen jetzt einen index-basierten Parameter '?1'
            jsonb_to_recordset(?1::jsonb) AS h(latitude float, longitude float, "timestamp" timestamptz)
        WHERE
            ph.visibility = 'PUBLIC'
            AND ST_DWithin(ph.location, ST_MakePoint(h.longitude, h.latitude)::geography, 500)
            AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        ORDER BY ph.uploaded_at DESC
        """, nativeQuery = true)
        // Die Signatur der Methode bleibt gleich, Spring Data ordnet ?1 automatisch dem ersten Parameter zu.
    List<Photo> findPhotosMatchingHistoricalBatch(@Param("historyJson") String historyJson);
}