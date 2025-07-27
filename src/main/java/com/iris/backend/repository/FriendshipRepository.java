package com.iris.backend.repository;

import com.iris.backend.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
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
}