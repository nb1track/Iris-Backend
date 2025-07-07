package com.chaptime.backend.repository;

import com.chaptime.backend.model.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository interface for managing Place entities.
 *
 * This interface provides methods for interacting with the `places` table
 * in the database. It extends JpaRepository, inheriting standard CRUD
 * operations, and defines custom query methods for specific use cases.
 *
 * Key functionalities:
 * - Retrieve a Place entity by its unique Google Place ID.
 * - Find places that have active public photos within a geographical radius
 *   during a specified time window.
 */
@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    @Query(value = """
    SELECT DISTINCT p.* FROM places p
    JOIN photos ph ON p.id = ph.place_id
    WHERE ph.visibility = 'PUBLIC'
      AND ph.uploaded_at BETWEEN (:timestamp - interval '5 hours') AND :timestamp
      AND ST_DWithin(p.location, ST_MakePoint(:longitude, :latitude)::geography, :radiusInMeters)
    """, nativeQuery = true)
    List<Place> findPlacesWithActivePublicPhotosInTimeWindow(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInMeters") double radiusInMeters,
            @Param("timestamp") OffsetDateTime timestamp
    );

    @Query(value = """
        SELECT DISTINCT p.*
        FROM
            places p
        JOIN
            photos ph ON p.id = ph.place_id,
            jsonb_to_recordset(:historyJson::jsonb) AS h(latitude float, longitude float, "timestamp" timestamptz)
        WHERE
            ph.visibility = 'PUBLIC'
            AND ST_DWithin(p.location, ST_MakePoint(h.longitude, h.latitude)::geography, :radiusInMeters)
            AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
        """, nativeQuery = true)
    List<Place> findPlacesMatchingHistoricalBatch(
            @Param("historyJson") String historyJson,
            @Param("radiusInMeters") double radiusInMeters
    );
}