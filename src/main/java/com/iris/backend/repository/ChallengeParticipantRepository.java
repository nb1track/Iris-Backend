package com.iris.backend.repository;
import com.iris.backend.model.ChallengeParticipant;
import com.iris.backend.model.ChallengeParticipantId;
import com.iris.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, ChallengeParticipantId> {
    // Zählt Teilnehmer für eine Challenge
    int countByChallengeId(UUID placeChallengeId);

    // Findet alle Teilnehmer (User-Objekte) für eine Challenge
    @Query("SELECT cp.user FROM ChallengeParticipant cp WHERE cp.challenge.id = :challengeId")
    List<User> findParticipantsByChallengeId(@Param("challengeId") UUID challengeId);

    // Prüft, ob ein User bereits Teilnehmer ist
    boolean existsById(ChallengeParticipantId id);

    @Query("SELECT COUNT(cp) FROM ChallengeParticipant cp WHERE cp.challenge.customPlace.id = :placeId")
    long countParticipantsByCustomPlaceId(@Param("placeId") UUID placeId);
}