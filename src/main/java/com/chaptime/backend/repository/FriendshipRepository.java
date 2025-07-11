package com.chaptime.backend.repository;

import com.chaptime.backend.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.FriendshipStatus;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing Friendship entities in the persistence layer.
 *
 * The FriendshipRepository provides methods to interact with the Friendship table
 * in the database. It supports basic CRUD operations as well as custom query
 * methods for retrieving friendships based on specific criteria.
 *
 * Methods include:
 * - Standard operations inherited from JpaRepository, such as saving, deleting,
 *   and finding Friendship entities by their primary key.
 * - A custom query method for retrieving friendships based on user and status.
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    List<Friendship> findByUserOneAndStatusOrUserTwoAndStatus(User userOne, FriendshipStatus statusOne, User userTwo, FriendshipStatus statusTwo);

    // Findet alle Anfragen, bei denen der User nicht der "action user" ist und der Status PENDING ist.
    List<Friendship> findAllByStatusAndUserTwoAndActionUserNot(FriendshipStatus status, User userTwo, User actionUser);
    List<Friendship> findAllByStatusAndUserOneAndActionUserNot(FriendshipStatus status, User userOne, User actionUser);
}