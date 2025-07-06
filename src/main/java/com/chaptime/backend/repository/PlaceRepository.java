package com.chaptime.backend.repository;

import com.chaptime.backend.model.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository interface for managing Place entities.
 *
 * This interface provides methods for interacting with the `places` table in the database.
 * It extends JpaRepository, allowing for standard CRUD operations and includes additional
 * custom queries for more specific use cases.
 *
 * Key functionalities:
 * - Retrieve a Place by its Google Place ID.
 * - Find places within a specified geographical radius that have active public photos.
 */
@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    @Query(value = """
    SELECT DISTINCT p.* FROM places p
    JOIN photos ph ON p.id = ph.place_id
    WHERE ph.visibility = 'PUBLIC'
      AND ph.expires_at > NOW()
      AND ST_DWithin(p.location, ST_MakePoint(:longitude, :latitude)::geography, :radiusInMeters)
    """, nativeQuery = true)
    List<Place> findPlacesWithActivePublicPhotos(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInMeters") double radiusInMeters
    );
}