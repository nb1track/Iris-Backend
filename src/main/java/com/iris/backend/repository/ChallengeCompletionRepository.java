package com.iris.backend.repository;
import com.iris.backend.model.ChallengeCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
public interface ChallengeCompletionRepository extends JpaRepository<ChallengeCompletion, UUID> {
    // Zählt die Abschlüsse für eine Challenge
    int countByChallengeId(UUID placeChallengeId);
    List<ChallengeCompletion> findByChallengeId(UUID placeChallengeId);
}