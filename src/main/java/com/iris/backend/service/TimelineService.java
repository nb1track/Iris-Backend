package com.iris.backend.service;

import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.model.TimelineEntry;
import com.iris.backend.model.User;
import com.iris.backend.repository.TimelineEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.model.Photo;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimelineService {

    private final TimelineEntryRepository timelineEntryRepository;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;

    public TimelineService(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository, PhotoService photoService) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
        this.photoService = photoService;
    }

    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> getTimelineForUser(User user) {
        // 1. Finde alle Timeline-Einträge für den Benutzer
        List<TimelineEntry> timelineEntries = timelineEntryRepository.findByUserOrderBySavedAtDesc(user);

        return timelineEntries.stream()
                // Extrahiere das Photo-Objekt aus dem Timeline-Eintrag
                .map(TimelineEntry::getPhoto)
                // Rufe die Hilfsmethode auf, die die signierten URLs korrekt generiert
                .map(photoService::toPhotoResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTimelineEntry(User user, UUID photoId) {
        // Finde zuerst das zugehörige Photo-Objekt
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        // Lösche den Timeline-Eintrag für diesen User und dieses Foto
        timelineEntryRepository.deleteByUserAndPhoto(user, photo);
    }
}