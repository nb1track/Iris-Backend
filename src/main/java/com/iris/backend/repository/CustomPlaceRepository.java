package com.iris.backend.repository;

import com.iris.backend.model.CustomPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.iris.backend.model.User;
import java.util.List;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomPlaceRepository extends JpaRepository<CustomPlace, UUID> {

    @Query(value = """
        SELECT * FROM custom_places cp
        WHERE cp.is_live = true
          AND cp.expires_at > NOW()
          AND ST_DWithin(
              cp.location,
              ST_MakePoint(:longitude, :latitude)::geography,
              cp.radius_meters
          )
        ORDER BY cp.created_at DESC -- Oder eine andere sinnvolle Sortierung
        """, nativeQuery = true)
    List<CustomPlace> findActivePlacesForUserLocation(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );

    List<CustomPlace> findAllByCreatorOrderByCreatedAtDesc(User creator);

    List<CustomPlace> findAllByIsTrendingTrueOrderByCreatedAtDesc();
}