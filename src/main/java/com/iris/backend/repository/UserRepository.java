package com.iris.backend.repository;

import com.iris.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing User entities.
 *
 * Provides methods for interacting with the `users` table in the database.
 * Extends JpaRepository, inheriting standard CRUD operations.
 *
 * Key functionalities:
 * - Retrieve a User entity by its Firebase UID.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query(value = """
    SELECT * FROM users u
    WHERE u.id != :userId
    AND u.last_location IS NOT NULL -- <-- DIESE ZEILE HINZUFÜGEN
    AND ST_DWithin(
        u.last_location,
        ST_MakePoint(:longitude, :latitude)::geography,
        :radius
    )
    """, nativeQuery = true)
    List<User> findUsersNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radius") double radius,
            @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM users WHERE id IN (:friendIds) AND ST_DWithin(last_location, :placeLocation, :radiusInMeters)", nativeQuery = true)
    List<User> findFriendsByIdsWithinRadius(@Param("friendIds") List<UUID> friendIds, @Param("placeLocation") Point placeLocation, @Param("radiusInMeters") double radiusInMeters);
}