package com.iris.backend.repository;

import com.iris.backend.model.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    List<User> findUsersNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radius") double radius,
            @Param("userId") UUID userId);

    /**
     * Findet alle Benutzer aus einer gegebenen Liste von IDs (Freunden),
     * die sich innerhalb eines bestimmten Radius um einen gegebenen Punkt befinden.
     * Dies ist die einzige Methode, die wir für diese Aufgabe brauchen.
     */
    @Query(value = """
        SELECT * FROM users u
        WHERE u.id IN (:friendIds)
        AND u.last_location IS NOT NULL
        AND ST_DWithin(u.last_location, :location, :radius, false)
        """, nativeQuery = true)
    List<User> findFriendsByIdsAndLocation(
            @Param("friendIds") List<UUID> friendIds,
            @Param("location") Point location,
            @Param("radius") double radius
    );
}