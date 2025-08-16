package com.iris.backend.service;

import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.model.TimelineEntry;
import com.iris.backend.model.User;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.repository.TimelineEntryRepository;
import org.slf4j.Logger; // NEUER IMPORT
import org.slf4j.LoggerFactory; // NEUER IMPORT
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TimelineService {

    private final TimelineEntryRepository timelineEntryRepository;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;
    // NEUEN LOGGER HINZUFÜGEN
    private static final Logger logger = LoggerFactory.getLogger(TimelineService.class);

    public TimelineService(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository, PhotoService photoService) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
        this.photoService = photoService;
    }

    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> getTimelineForUser(User user) {
        // --- NEUES LOGGING ---
        logger.info("====== Starting timeline sync for user: {} ======", user.getUsername());

        // 1. Finde alle Timeline-Einträge für den Benutzer
        List<TimelineEntry> timelineEntries = timelineEntryRepository.findByUserOrderBySavedAtDesc(user);
        logger.info("--- Found {} timeline entries for user '{}'.", timelineEntries.size(), user.getUsername());

        // 2. Wandle sie in DTOs mit signierten URLs um
        List<PhotoResponseDTO> timelinePhotos = timelineEntries.stream()
                .map(TimelineEntry::getPhoto)
                .map(photoService::toPhotoResponseDTO)
                .collect(Collectors.toList());

        // Optional: Logge die erste URL, um zu prüfen, ob sie korrekt aussieht
        if (!timelinePhotos.isEmpty()) {
            logger.info("--- Sample generated photo URL: {}", timelinePhotos.get(0).storageUrl());
        }

        logger.info("====== Finished timeline sync. Returning {} photos. ======", timelinePhotos.size());
        // --- ENDE LOGGING ---

        return timelinePhotos;
    }

    @Transactional
    public void deleteTimelineEntry(User user, UUID photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        timelineEntryRepository.deleteByUserAndPhoto(user, photo);
        logger.info("--- Deleted timeline entry for user '{}' and photoId '{}'", user.getUsername(), photoId);
    }
}