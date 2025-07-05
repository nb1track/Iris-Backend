package com.chaptime.backend.repository;

import com.chaptime.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Spring Data JPA erstellt die Methoden automatisch aus dem Namen.
    // z.B. findByUsername(String username);
}