package com.chaptime.backend.controller;

import com.chaptime.backend.dto.TimelineEntryRequestDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.User;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.repository.TimelineEntryRepository;
import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.service.TimelineService;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {

    private final TimelineEntryRepository timelineEntryRepository;
    private final PhotoRepository photoRepository;
    private final TimelineService timelineService;

    /**
     * Constructs a new TimelineController with the specified dependencies.
     *
     * @param timelineEntryRepository the repository responsible for managing TimelineEntry entities
     * @param photoRepository the repository responsible for managing Photo entities
     */
    public TimelineController(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository, TimelineService timelineService) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
        this.timelineService = timelineService;
    }

    /**
     * Saves a photo to the current user's timeline.
     *
     * This method associates a specific photo, identified by its ID in the request,
     * with the authenticated user, and creates a new timeline entry in the database.
     * If the photo does not exist, an exception is thrown.
     *
     * @param currentUser*/
    @PostMapping("/entries")
    @Transactional
    public ResponseEntity<Void> savePhotoToTimeline(
            @AuthenticationPrincipal User currentUser,
            @RequestBody TimelineEntryRequestDTO request) {

        // Finde das Foto, das gespeichert werden soll
        Photo photoToSave = photoRepository.findById(request.photoId())
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        TimelineEntry newEntry = new TimelineEntry();
        newEntry.setUser(currentUser);
        newEntry.setPhoto(photoToSave);

        timelineEntryRepository.save(newEntry);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves the timeline for the currently authenticated user.
     * The timeline consists of a list of photos associated with the user.
     *
     * @param currentUser the currently authenticated user whose timeline needs to be retrieved
     * @return a ResponseEntity containing a list of PhotoResponseDTO objects representing the photos in the user's timeline
     */
    @GetMapping
    public ResponseEntity<List<PhotoResponseDTO>> getTimeline(@AuthenticationPrincipal User currentUser) {
        List<PhotoResponseDTO> timelinePhotos = timelineService.getTimelineForUser(currentUser);
        return ResponseEntity.ok(timelinePhotos);
    }

    /**
     * Deletes a timeline entry for the currently authenticated user.
     *
     * This method removes an existing timeline entry associated with the specified photo ID from the
     * currently authenticated user's timeline. If the timeline entry does not exist, an exception is thrown.
     *
     * @param currentUser the currently authenticated user requesting the deletion
     * @param photoId the unique identifier of the photo whose timeline entry is to be deleted
     * @return a {@code ResponseEntity<Void>} representing the HTTP response with a 204 No Content status upon successful deletion
     */
    @DeleteMapping("/entries/{photoId}")
    public ResponseEntity<Void> deleteTimelineEntry(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID photoId) {

        timelineService.deleteTimelineEntry(currentUser, photoId);
        return ResponseEntity.noContent().build(); // 204 No Content bei Erfolg
    }
}