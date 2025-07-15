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
        -- Benutze den benannten Parameter :historyJson
        jsonb_to_recordset(:historyJson::jsonb) AS h(latitude float, longitude float, "timestamp" timestamptz)
    WHERE
        ph.visibility = 'PUBLIC'
        -- Benutze den benannten Parameter :radiusInMeters
        AND ST_DWithin(ph.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
        AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
    ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
        List<Photo> findPhotosMatchingHistoricalBatch(
                @Param("historyJson") String historyJson,
                @Param("radiusInMeters") double radiusInMeters
    );
}
