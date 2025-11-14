package com.iris.backend.repository;
import com.iris.backend.model.CustomPlaceChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CustomPlaceChallengeRepository extends JpaRepository<CustomPlaceChallenge, UUID> {
    // Findet alle Challenges für einen Place
    List<CustomPlaceChallenge> findByCustomPlaceId(UUID customPlaceId);
}