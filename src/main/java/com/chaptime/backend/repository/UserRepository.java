package com.chaptime.backend.repository;

import com.chaptime.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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
}