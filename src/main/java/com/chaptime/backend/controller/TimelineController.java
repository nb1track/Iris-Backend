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

    public TimelineController(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
    }

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