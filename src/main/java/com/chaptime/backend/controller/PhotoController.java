package com.chaptime.backend.controller;

import com.chaptime.backend.dto.PhotoResponseDTO; // Import hinzufügen
import java.util.List;
import com.chaptime.backend.dto.PhotoUploadRequest;
import com.chaptime.backend.dto.PhotoUploadResponse;
import com.chaptime.backend.model.User;
import com.chaptime.backend.repository.UserRepository;
import com.chaptime.backend.service.PhotoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper; // Jackson's Tool zum Umwandeln

    public PhotoController(PhotoService photoService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.photoService = photoService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") String metadataJson // <-- HIER IST DIE ÄNDERUNG
    ) throws JsonProcessingException { // <-- Exception für die Umwandlung hinzufügen

        // Manuelle Umwandlung von Text zu Java-Objekt
        PhotoUploadRequest metadata = objectMapper.readValue(metadataJson, PhotoUploadRequest.class);

        User uploader = userRepository.findById(metadata.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UUID newPhotoId = photoService.createPhoto(
                file,
                metadata.latitude(),
                metadata.longitude(),
                metadata.visibility(),
                uploader
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new PhotoUploadResponse(newPhotoId));
    }

    @GetMapping("/discover")
    public ResponseEntity<List<PhotoResponseDTO>> getDiscoverablePhotos(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "500.0") double radius // Radius in Metern, 500m ist der Standard
    ) {
        List<PhotoResponseDTO> discoverablePhotos = photoService.findDiscoverablePhotos(latitude, longitude, radius);
        return ResponseEntity.ok(discoverablePhotos);
    }


    @GetMapping("/feed")
    public ResponseEntity<List<PhotoResponseDTO>> getFriendsFeed(@RequestParam UUID userId) {
        List<PhotoResponseDTO> feedPhotos = photoService.getFriendsFeed(userId);
        return ResponseEntity.ok(feedPhotos);
    }
}