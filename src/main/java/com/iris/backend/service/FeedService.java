package com.iris.backend.service;

import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.repository.FeedRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final ObjectMapper objectMapper;
    private final GoogleApiService googleApiService; // NEU: Google Service für die Dichte-Prüfung
    private final GcsStorageService gcsStorageService;

    public FeedService(FeedRepository feedRepository, ObjectMapper objectMapper, GoogleApiService googleApiService, GcsStorageService gcsStorageService) {
        this.feedRepository = feedRepository;
        this.objectMapper = objectMapper;
        this.googleApiService = googleApiService; // NEU
        this.gcsStorageService = gcsStorageService;
    }

    /**
     * Generates a historical feed based on the provided location history.
     *
     * This method analyzes a list of historical location points, determines the
     * density of nearby locations, and computes an adaptive radius for querying
     * location-based photos. It groups the retrieved photos by their corresponding
     * places and returns a list of PlaceDTOs, each containing relevant photos.
     *
     * @param history The list of historical location data points, represented as
     *                HistoricalPointDTO objects, which include latitude, longitude,
     *                and timestamp information. Must not be null or empty.
     * @return A list of PlaceDTO objects representing grouped locations, each
     *         containing their associated photo data. Returns an empty list if the
     *         input history is null or empty.
     */
    @Transactional(readOnly = true)
    public List<PlaceDTO> generateHistoricalFeed(List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // ... (deine Logik für den adaptiven Radius bleibt unverändert)
        HistoricalPointDTO latestPoint = history.get(history.size() - 1);
        List<PlaceDTO> nearbyPlacesSample = googleApiService.findNearbyPlaces(latestPoint.latitude(), latestPoint.longitude());
        double adaptiveRadius;
        if (nearbyPlacesSample.size() > 10) {
            adaptiveRadius = 50;
        } else if (nearbyPlacesSample.size() > 2) {
            adaptiveRadius = 100;
        } else {
            adaptiveRadius = 300;
        }
        System.out.println("ADAPTIVE RADIUS SET TO: " + adaptiveRadius + "m based on " + nearbyPlacesSample.size() + " nearby places.");
        // ---

        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Photo> photos = feedRepository.findPhotosMatchingHistoricalBatch(historyJson, adaptiveRadius);

            // Gruppiere die gefundenen Fotos nach ihrem Ort (Place)
            Map<PlaceDTO, List<PhotoResponseDTO>> groupedByPlace = photos.stream()
                    // ===== KORREKTUR: Füge diese Filterzeile hinzu =====
                    .filter(photo -> photo.getPlace() != null) // Verhindert die NullPointerException
                    .collect(Collectors.groupingBy(
                            // Schlüssel für die Gruppierung: das PlaceDTO des Fotos
                            photo -> new PlaceDTO(
                                    photo.getPlace().getId(),
                                    photo.getPlace().getGooglePlaceId(),
                                    photo.getPlace().getName(),
                                    photo.getPlace().getAddress(),
                                    null // Foto-Liste ist hier noch nicht relevant
                            ),
                            // Werte: eine Liste der PhotoResponseDTOs für jeden Ort
                            Collectors.mapping(
                                    photo -> new PhotoResponseDTO(
                                            photo.getId(),
                                            gcsStorageService.generateSignedUrl(photo.getStorageUrl()),
                                            photo.getUploadedAt(),
                                            photo.getPlace().getId().intValue(),
                                            photo.getPlace().getName(),
                                            photo.getUploader().getId(),
                                            photo.getUploader().getUsername()
                                    ),
                                    Collectors.toList()
                            )
                    ));

            // Wandle die Map in die finale Listenstruktur um
            return groupedByPlace.entrySet().stream()
                    .map(entry -> new PlaceDTO(
                            entry.getKey().id(),
                            entry.getKey().googlePlaceId(),
                            entry.getKey().name(),
                            entry.getKey().address(),
                            entry.getValue() // Füge die Liste der Fotos hinzu
                    ))
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }
}