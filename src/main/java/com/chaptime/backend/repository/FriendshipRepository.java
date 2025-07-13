package com.chaptime.backend.repository;

import com.chaptime.backend.model.Friendship;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.FriendshipStatus;
import java.util.List;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    // Diese Methode hattest du schon für die Freundesliste
    List<Friendship> findByUserOneAndStatusOrUserTwoAndStatus(User userOne, FriendshipStatus statusOne, User userTwo, FriendshipStatus statusTwo);

    // Diese beiden Methoden sind NEU und werden für die offenen Anfragen gebraucht
    List<Friendship> findByUserOneAndStatusAndActionUserNot(User userOne, FriendshipStatus status, User actionUser);
    List<Friendship> findByUserTwoAndStatusAndActionUserNot(User userTwo, FriendshipStatus status, User actionUser);

    // Findet alle Freundschafts-Beziehungen für einen bestimmten User
    List<Friendship> findByUserOneOrUserTwo(User userOne, User userTwo);

    // Diese Methode hattest du schon, um doppelte Anfragen zu verhindern
    boolean existsByUserOneAndUserTwo(User userOne, User userTwo);

    // Wir fügen nativeQuery = true hinzu und passen die Abfrage an reines SQL an
    @Query(value = "SELECT * FROM users u WHERE u.id IN :friendIds AND ST_DWithin(u.last_location, :placeLocation, :radiusInMeters)", nativeQuery = true)
    List<User> findFriendsByIdsWithinRadius(@Param("friendIds") List<UUID> friendIds, @Param("placeLocation") Point placeLocation, @Param("radiusInMeters") double radiusInMeters);
}