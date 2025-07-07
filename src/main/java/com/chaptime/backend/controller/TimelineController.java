package com.chaptime.backend.controller;

import com.chaptime.backend.dto.TimelineEntryRequestDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.User;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.repository.TimelineEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timeline/entries")
public class TimelineController {

    private final TimelineEntryRepository timelineEntryRepository;
    private final PhotoRepository photoRepository;

    /**
     * Constructs a new TimelineController with the specified dependencies.
     *
     * @param timelineEntryRepository the repository responsible for managing TimelineEntry entities
     * @param photoRepository the repository responsible for managing Photo entities
     */
    public TimelineController(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
    }

    /**
     * Saves a photo to the current user's timeline.
     *
     * This method associates a specific photo, identified by its ID in the request,
     * with the authenticated user, and creates a new timeline entry in the database.
     * If the photo does not exist, an exception is thrown.
     *
     * @param currentUser*/
    @PostMapping
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
}