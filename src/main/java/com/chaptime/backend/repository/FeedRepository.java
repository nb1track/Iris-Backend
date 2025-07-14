package com.chaptime.backend.repository;

import com.chaptime.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

@Repository
public interface FeedRepository extends JpaRepository<Photo, UUID> {

    @Query(value = """
        SELECT DISTINCT ph.*
        FROM
            photos ph,
            jsonb_to_recordset(:historyJson::jsonb) AS h(latitude float, longitude float, "timestamp" timestamptz)
        WHERE
            ph.visibility = 'PUBLIC'
            -- Der Radius wird jetzt als dynamischer Parameter übergeben
            AND ST_DWithin(ph.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
            AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        ORDER BY ph.uploaded_at DESC
        """, nativeQuery = true)
        // Die Methode akzeptiert jetzt den Radius als Parameter
    List<Photo> findPhotosMatchingHistoricalBatch(
            @Param("historyJson") String historyJson,
            @Param("radiusInMeters") double radiusInMeters
    );
}