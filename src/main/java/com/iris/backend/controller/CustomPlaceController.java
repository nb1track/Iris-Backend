package com.iris.backend.controller;

import com.iris.backend.dto.*;
import com.iris.backend.dto.feed.GalleryFeedItemDTO; // NEUES DTO
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.service.ChallengeService;
import com.iris.backend.service.CustomPlaceService;
import com.iris.backend.service.GalleryFeedService; // NEUER SERVICE
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custom-places")
public class CustomPlaceController {

    private final CustomPlaceService customPlaceService;
    private final GalleryFeedService galleryFeedService;
    private final ChallengeService challengeService;

    // Konstruktor bereinigt: PlaceService ist entfernt
    public CustomPlaceController(CustomPlaceService customPlaceService,
                                 GalleryFeedService galleryFeedService,
                                 ChallengeService challengeService) {
        this.customPlaceService = customPlaceService;
        this.galleryFeedService = galleryFeedService;
        this.challengeService = challengeService;
    }

    /**
     * Erstellt einen neuen Custom Place (Iris Spot).
     * KORRIGIERT: Gibt jetzt ein GalleryFeedItemDTO zurück, um Serialisierungsfehler
     * des 'Point'-Objekts zu vermeiden und konsistent mit anderen Endpunkten zu sein.
     */
    @PostMapping
    public ResponseEntity<GalleryFeedItemDTO> createCustomPlace( // <-- RÜCKGABETYP GEÄNDERT
                                                                 @RequestBody CreateCustomPlaceRequestDTO request,
                                                                 @AuthenticationPrincipal User currentUser) {

        // 1. Erstelle den Spot in der Datenbank (gibt das Entity zurück)
        CustomPlace newPlaceEntity = customPlaceService.createCustomPlace(request, currentUser);

        // 2. Konvertiere das Entity in ein DTO für die Antwort
        //    'false', da ein neuer Spot per Definition noch keine Fotos hat.
        GalleryFeedItemDTO newPlaceDTO = galleryFeedService.getFeedItemForPlace(newPlaceEntity, false);

        // 3. Gib das DTO zurück
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlaceDTO); // <-- BODY GEÄNDERT
    }

    /**
     * Holt die Teilnehmer eines Custom Place (User, die dort hochgeladen haben).
     */
    @GetMapping("/{placeId}/participants")
    public ResponseEntity<List<UserDTO>> getParticipantsForPlace(
            @PathVariable UUID placeId,
            @AuthenticationPrincipal User currentUser) {
        try {
            List<UserDTO> participants = customPlaceService.getParticipants(placeId, currentUser);
            return ResponseEntity.ok(participants);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Holt den Feed "Trending Spots".
     * Nutzt den neuen GalleryFeedService und das GalleryFeedItemDTO.
     */
    @GetMapping("/trending")
    public ResponseEntity<List<GalleryFeedItemDTO>> getTrendingSpots() {
        List<GalleryFeedItemDTO> trendingSpots = galleryFeedService.getTrendingSpots();
        return ResponseEntity.ok(trendingSpots);
    }


    /**
     * Holt den Feed "Meine erstellten Spots" für den eingeloggten User.
     * Nutzt den neuen GalleryFeedService und das GalleryFeedItemDTO.
     */
    @GetMapping("/my-spots")
    public ResponseEntity<List<GalleryFeedItemDTO>> getMyCreatedSpots(
            @AuthenticationPrincipal User currentUser) {

        List<GalleryFeedItemDTO> mySpots = galleryFeedService.getMyCreatedSpots(currentUser);
        return ResponseEntity.ok(mySpots);
    }

    /**
     * NEU: Holt alle Challenges für einen bestimmten Custom Place.
     * Entspricht GET /place/challenges?place_id=... aus challenges.md
     */
    @GetMapping("/{placeId}/challenges")
    public ResponseEntity<List<ChallengeDTO>> getChallengesForPlace(
            @PathVariable UUID placeId,
            @AuthenticationPrincipal User currentUser) {

        // Die user_id aus der Query (challenges.md) ist nicht nötig,
        // da wir den User sicher aus dem Token bekommen.
        List<ChallengeDTO> challenges = challengeService.getChallengesForPlace(placeId, currentUser);
        return ResponseEntity.ok(challenges);
    }

    /**
     * NEU: Lässt einen User einer Challenge beitreten.
     * Entspricht POST /place/challenges aus challenges.md
     */
    @PostMapping("/challenges/join")
    public ResponseEntity<Void> joinChallenge(
            @RequestBody @Valid JoinChallengeRequestDTO request,
            @AuthenticationPrincipal User currentUser) {

        // Die user_id im Body (challenges.md) ist nicht nötig,
        // da wir den User sicher aus dem Token bekommen.
        challengeService.joinChallenge(request, currentUser);
        return ResponseEntity.ok().build();
    }

    /**
     * NEU: Holt den detaillierten Inhalt einer einzelnen Challenge.
     * (Entspricht GET /place/challenges?challenge_id=... aus challenge_content.md)
     */
    @GetMapping("/challenges/{challengeId}")
    public ResponseEntity<ChallengeContentDTO> getChallengeContent(
            @PathVariable UUID challengeId,
            @AuthenticationPrincipal User currentUser) {

        ChallengeContentDTO content = challengeService.getChallengeContent(challengeId, currentUser);
        return ResponseEntity.ok(content);
    }

}