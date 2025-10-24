package com.iris.backend.repository;

import com.iris.backend.model.GooglePlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface GooglePlaceRepository extends JpaRepository<GooglePlace, Long> {
    Optional<GooglePlace> findByGooglePlaceId(String googlePlaceId);

    @Query(value = """
    SELECT DISTINCT p.* FROM google_places p
    JOIN photos ph ON p.id = ph.google_place_id 
    WHERE ph.visibility = 'PUBLIC'
      AND ph.uploaded_at BETWEEN (:timestamp - interval '5 hours') AND :timestamp
      AND ST_DWithin(p.location, ST_MakePoint(:longitude, :latitude)::geography, :radiusInMeters)
    """, nativeQuery = true)
    List<GooglePlace> findPlacesWithActivePublicPhotosInTimeWindow(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInMeters") double radiusInMeters,
            @Param("timestamp") OffsetDateTime timestamp
    );

    @Query(value = """
        SELECT DISTINCT p.*
        FROM
            google_places p
        JOIN
            photos ph ON p.id = ph.google_place_id,
            jsonb_to_recordset(CAST(:historyJson AS jsonb)) AS h(latitude float, longitude float, "timestamp" timestamptz)
        WHERE
            ph.visibility = 'PUBLIC'
            AND ST_DWithin(p.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
            AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        """, nativeQuery = true)
    List<GooglePlace> findPlacesMatchingHistoricalBatch(
            @Param("historyJson") String historyJson,
            @Param("radiusInMeters") double radiusInMeters
    );

    @Query(value = """
        SELECT * FROM google_places p
        WHERE ST_DWithin(p.location, ST_MakePoint(:longitude, :latitude)::geography, :radiusInMeters)
        """, nativeQuery = true)
    List<GooglePlace> findPlacesWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInMeters") double radiusInMeters
    );

    @Query(value = """
    SELECT * FROM google_places p
    WHERE ST_DWithin(
        p.location,
        ST_MakePoint(:longitude, :latitude)::geography,
        p.radius_meters
    )
    ORDER BY p.importance DESC
    """, nativeQuery = true)
    List<GooglePlace> findActivePlacesForUserLocation(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );
}