package com.iris.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PlaceService {

    private final GooglePlaceRepository googlePlaceRepository;
    private final CustomPlaceRepository customPlaceRepository;
    private final GoogleApiService googleApiService;
    private final ObjectMapper objectMapper;

    public PlaceService(GooglePlaceRepository googlePlaceRepository,
                        CustomPlaceRepository customPlaceRepository,
                        GoogleApiService googleApiService,
                        ObjectMapper objectMapper) {
        this.googlePlaceRepository = googlePlaceRepository;
        this.customPlaceRepository = customPlaceRepository;
        this.googleApiService = googleApiService;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds all active places (Google and Custom) for the user's current location.
     * The filtering happens entirely in the database based on each place's individual radius.
     */
    public List<PlaceDTO> findActiveNearbyPlaces(double latitude, double longitude) {
        // Schritt 1: Google API anfragen, um unsere lokale DB mit den neuesten
        // Orten in der Umgebung zu aktualisieren ("Cache aufwärmen").
        googleApiService.findNearbyPlaces(latitude, longitude);

        // Schritt 2: Unsere Datenbank nach aktiven Orten abfragen.
        List<GooglePlace> activeGooglePlaces = googlePlaceRepository.findActivePlacesForUserLocation(latitude, longitude);
        List<CustomPlace> activeCustomPlaces = customPlaceRepository.findActivePlacesForUserLocation(latitude, longitude);

        // Schritt 3: Beide Ergebnislisten in DTOs umwandeln.
        Stream<PlaceDTO> googlePlaceDTOs = activeGooglePlaces.stream()
                .map(p -> new PlaceDTO(p.getId(), p.getGooglePlaceId(), p.getName(), p.getAddress(), null, p.getRadiusMeters(), p.getImportance()));

        Stream<PlaceDTO> customPlaceDTOs = activeCustomPlaces.stream()
                .map(p -> new PlaceDTO(null, "custom_" + p.getId(), p.getName(), "Custom Location", null, p.getRadiusMeters(), 0)); // Custom Places haben eine Wichtigkeit von 0

        // Schritt 4: DTO-Listen zusammenführen und nach Wichtigkeit sortieren.
        return Stream.concat(googlePlaceDTOs, customPlaceDTOs)
                .sorted((p1, p2) -> p2.importance().compareTo(p1.importance())) // Höchste Wichtigkeit zuerst
                .collect(Collectors.toList());
    }

    /**
     * Finds historical galleries based on a list of historical points.
     * This is a separate feature and remains unchanged.
     */
    @Transactional(readOnly = true)
    public List<PlaceDTO> findHistoricalGalleriesBatch(List<HistoricalPointDTO> history, double radius) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<GooglePlace> googlePlaces = googlePlaceRepository.findPlacesMatchingHistoricalBatch(historyJson, radius);
            return googlePlaces.stream()
                    .map(p -> new PlaceDTO(p.getId(), p.getGooglePlaceId(), p.getName(), p.getAddress(), null, p.getRadiusMeters(), p.getImportance()))
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }

    /**
     * Retrieves a list of trending spots by fetching all custom places marked as trending,
     * and transforms them into PlaceDTO objects sorted by their creation date in descending order.
     *
     * @return a list of PlaceDTO objects representing the trending custom places
     */
    public List<PlaceDTO> findTrendingSpots() {
        List<CustomPlace> trendingCustomPlaces = customPlaceRepository.findAllByIsTrendingTrueOrderByCreatedAtDesc();

        // Wandle die CustomPlaces in das allgemeine PlaceDTO um
        return trendingCustomPlaces.stream()
                .map(p -> new PlaceDTO(null, "custom_" + p.getId(), p.getName(), "Custom Location", null, p.getRadiusMeters(), 0))
                .collect(Collectors.toList());
    }
}