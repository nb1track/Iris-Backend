package com.iris.backend.service;

import com.iris.backend.dto.*;
import com.iris.backend.model.*;
import com.iris.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    private final CustomPlaceChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeCompletionRepository completionRepository;
    private final UserService userService;
    private final PhotoService photoService;

    public ChallengeService(CustomPlaceChallengeRepository challengeRepository,
                            ChallengeParticipantRepository participantRepository,
                            ChallengeCompletionRepository completionRepository,
                            UserService userService,
                            PhotoService photoService) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.completionRepository = completionRepository;
        this.userService = userService;
        this.photoService = photoService;
    }

    @Transactional(readOnly = true)
    public List<ChallengeDTO> getChallengesForPlace(UUID placeId, User currentUser) {
        List<CustomPlaceChallenge> challenges = challengeRepository.findByCustomPlaceId(placeId);

        return challenges.stream()
                .map(challenge -> toChallengeDTO(challenge, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinChallenge(JoinChallengeRequestDTO request, User currentUser) {
        UUID challengeId = request.challengeId();

        CustomPlaceChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RuntimeException("Challenge not found"));

        ChallengeParticipantId participantId = new ChallengeParticipantId(currentUser.getId(), challengeId);

        if (participantRepository.existsById(participantId)) {
            // User ist bereits gejoint, nichts zu tun
            return;
        }

        ChallengeParticipant newParticipant = new ChallengeParticipant();
        newParticipant.setId(participantId);
        newParticipant.setUser(currentUser);
        newParticipant.setChallenge(challenge);

        participantRepository.save(newParticipant);
    }

    @Transactional(readOnly = true)
    public ChallengeContentDTO getChallengeContent(UUID challengeId, User currentUser) {
        // 1. Challenge-Hauptobjekt holen
        CustomPlaceChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RuntimeException("Challenge not found"));

        ChallengeDefinition definition = challenge.getChallengeDefinition();

        // 2. Alle Teilnehmer und Abschlüsse holen
        List<User> participants = participantRepository.findParticipantsByChallengeId(challengeId);
        List<ChallengeCompletion> completions = completionRepository.findByChallengeId(challengeId);

        // 3. Basis-Infos berechnen (Progress, Joined-Status)
        boolean isJoined = participants.stream()
                .anyMatch(p -> p.getId().equals(currentUser.getId()));

        int progress = calculateProgress(challengeId, participants.size());

        // 4. "images" Liste erstellen (Alle Fotos der Challenge)
        List<PhotoResponseDTO> allImages = completions.stream()
                .map(ChallengeCompletion::getPhoto)
                .filter(photo -> photo != null) // Filtern, falls Foto gelöscht wurde
                .map(photoService::toPhotoResponseDTO) // Foto-Entity -> DTO
                .sorted(Comparator.comparing(PhotoResponseDTO::timestamp).reversed()) // Neueste zuerst
                .collect(Collectors.toList());

        // 5. "ranking" Liste erstellen (Top 3 nach Likes)
        AtomicInteger rank = new AtomicInteger(1); // Für Rank 1, 2, 3
        List<RankingItemDTO> ranking = allImages.stream()
                .sorted(Comparator.comparingInt(PhotoResponseDTO::likeCount).reversed()) // Meiste Likes zuerst
                .limit(3)
                .map(dto -> new RankingItemDTO( // PhotoResponseDTO -> RankingItemDTO
                        rank.getAndIncrement(),
                        dto.photoId(),
                        dto.storageUrl(),
                        dto.timestamp(),
                        dto.placeType(),
                        dto.googlePlaceId(),
                        dto.customPlaceId(),
                        dto.placeName(),
                        dto.userId(),
                        dto.username(),
                        dto.profileImageUrl(),
                        dto.likeCount()
                ))
                .collect(Collectors.toList());

        // 6. Finale DTO zusammensetzen
        return new ChallengeContentDTO(
                challenge.getId(),
                definition.getName(),
                progress,
                definition.getIconKey(),
                isJoined,
                allImages,
                ranking
        );
    }
    private ChallengeDTO toChallengeDTO(CustomPlaceChallenge challenge, User currentUser) {
        // 1. Teilnehmer holen und in DTOs umwandeln
        List<User> participants = participantRepository.findParticipantsByChallengeId(challenge.getId());
        List<UserDTO> participantDTOs = participants.stream()
                .map(userService::getUserProfile) // Wiederverwenden der Logik für signierte URLs!
                .collect(Collectors.toList());

        // 2. Prüfen, ob der aktuelle User gejoint ist
        boolean isJoined = participants.stream()
                .anyMatch(p -> p.getId().equals(currentUser.getId()));

        // 3. Progress berechnen
        int progress = calculateProgress(challenge.getId(), participants.size());

        // 4. Definition-Infos holen
        ChallengeDefinition definition = challenge.getChallengeDefinition();

        return new ChallengeDTO(
                challenge.getId(),
                definition.getName(),
                progress,
                definition.getIconKey(),
                isJoined,
                participantDTOs
        );
    }

    private int calculateProgress(UUID challengeId, int participantCount) {
        if (participantCount == 0) {
            return 0; // Keine Division durch Null
        }

        // Zähle, wie viele Abschlüsse (z.B. Foto-Uploads) es gibt
        int completionCount = completionRepository.countByChallengeId(challengeId);

        // Berechne % (z.B. 3 Abschlüsse / 10 Teilnehmer * 100 = 30%)
        return (int) Math.round(((double) completionCount / participantCount) * 100);
    }
}