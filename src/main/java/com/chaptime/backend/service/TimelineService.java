package com.chaptime.backend.service;

import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.User;
import com.chaptime.backend.repository.TimelineEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chaptime.backend.repository.PhotoRepository;
import com.chaptime.backend.model.Photo;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimelineService {

    private final TimelineEntryRepository timelineEntryRepository;
    private final PhotoRepository photoRepository;

    public TimelineService(TimelineEntryRepository timelineEntryRepository, PhotoRepository photoRepository) {
        this.timelineEntryRepository = timelineEntryRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional(readOnly = true)
    public List<PhotoResponseDTO> getTimelineForUser(User user) {
        // 1. Finde alle Timeline-Einträge für den Benutzer
        List<TimelineEntry> timelineEntries = timelineEntryRepository.findByUserOrderBySavedAtDesc(user);

        // 2. Extrahiere die Foto-Informationen und wandle sie in DTOs um
        return timelineEntries.stream()
                .map(entry -> {
                    // Hole das Foto aus dem Timeline-Eintrag
                    var photo = entry.getPhoto();
                    // Erstelle ein DTO mit den Foto-Details
                    return new PhotoResponseDTO(
                            photo.getId(),
                            photo.getStorageUrl(),
                            photo.getUploadedAt(),
                            photo.getPlace().getId().intValue(),
                            photo.getPlace().getName(),
                            photo.getUploader().getId(),
                            photo.getUploader().getUsername()
                    );
                })
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