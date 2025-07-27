package com.iris.backend.repository;

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
    SELECT DISTINCT ph.*
    FROM
        photos ph,
        -- KORREKTUR: Benutze CAST() anstatt '::' für die Typumwandlung
        jsonb_to_recordset(CAST(:historyJson AS jsonb)) AS h(latitude float, longitude float, "timestamp" timestamptz)
    WHERE
        ph.visibility = 'PUBLIC'
        AND ST_DWithin(ph.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
        AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
    ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
    List<Photo> findPhotosMatchingHistoricalBatch(
            @Param("historyJson") String historyJson,
            @Param("radiusInMeters") double radiusInMeters
    );
}