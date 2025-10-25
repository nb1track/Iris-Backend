package com.iris.backend.controller;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO; // NEUES DTO
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.service.CustomPlaceService;
import com.iris.backend.service.GalleryFeedService; // NEUER SERVICE
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
    private final GalleryFeedService galleryFeedService; // NEU

    // Konstruktor bereinigt: PlaceService ist entfernt
    public CustomPlaceController(CustomPlaceService customPlaceService,
                                 GalleryFeedService galleryFeedService) {
        this.customPlaceService = customPlaceService;
        this.galleryFeedService = galleryFeedService;
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
}