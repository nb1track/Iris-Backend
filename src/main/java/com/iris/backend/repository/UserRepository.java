package com.iris.backend.repository;

import com.iris.backend.model.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    /**
     * Findet alle Benutzer aus einer gegebenen Liste von IDs (Freunden),
     * die sich innerhalb eines bestimmten Radius um einen gegebenen Punkt befinden.
     * Diese Methode wird für die "Freunde in der Nähe"-Funktion an einem Ort verwendet.
     */
    @Query(value = """
        SELECT * FROM users u
        WHERE u.id IN :friendIds
        AND u.last_location IS NOT NULL
        AND ST_DWithin(u.last_location, :location, :radius, false)
        """, nativeQuery = true)
    List<User> findFriendsByIdsAndLocation(
            @Param("friendIds") List<UUID> friendIds,
            @Param("location") Point location,
            @Param("radius") double radius
    );

    /**
     * Finds all users within a given radius of a location, excluding a specific user.
     * This uses a native query for spatial search and replaces the old, problematic method.
     */
    @Query(value = """
        SELECT * FROM users u
        WHERE u.id != :currentUserId
        AND u.last_location IS NOT NULL
        AND ST_DWithin(u.last_location, ST_MakePoint(:longitude, :latitude), :radius, false)
        """, nativeQuery = true)
    List<User> findNearbyUsersByLocation(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radius") double radius,
            @Param("currentUserId") UUID currentUserId
    );

    /**
     * Sucht nach Benutzern, deren Benutzername den Suchbegriff enthält (case-insensitive).
     * Der aktuelle Benutzer wird von den Ergebnissen ausgeschlossen.
     * Die Ergebnisse werden paginiert zurückgegeben.
     *
     * @param query Der Suchbegriff.
     * @param currentUserId Die ID des suchenden Benutzers, der ausgeschlossen werden soll.
     * @param pageable Das Pageable-Objekt, das Paginierungs- und Sortierinformationen enthält.
     * @return Eine Seite (Page) von User-Objekten.
     */
    @Query("SELECT u FROM User u WHERE u.username ILIKE %:query% AND u.id != :currentUserId")
    Page<User> searchUsers(String query, UUID currentUserId, Pageable pageable);
}