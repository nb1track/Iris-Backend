package com.chaptime.backend.repository;

import com.chaptime.backend.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User; // Import hinzufügen
import com.chaptime.backend.model.enums.FriendshipStatus; // Import hinzufügen
import java.util.List; // Import hinzufügen

import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    List<Friendship> findByUserOneAndStatusOrUserTwoAndStatus(User userOne, FriendshipStatus statusOne, User userTwo, FriendshipStatus statusTwo);

}