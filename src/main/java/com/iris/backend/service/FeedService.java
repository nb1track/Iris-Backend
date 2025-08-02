package com.iris.backend.service;

import com.iris.backend.dto.FeedPlaceDTO;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.model.Place;
import com.iris.backend.repository.FeedRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.sql.Timestamp;

@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final ObjectMapper objectMapper;
    private final GoogleApiService googleApiService;
    // NEU: Wir injizieren den PhotoService, um seine Konvertierungslogik zu nutzen
    private final PhotoService photoService;
    private final GcsStorageService gcsStorageService;
    private final String photosBucketName;

    public FeedService(
            FeedRepository feedRepository,
            ObjectMapper objectMapper,
            GoogleApiService googleApiService,
            PhotoService photoService, // NEU: PhotoService hier hinzufügen
            GcsStorageService gcsStorageService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName) {
        this.feedRepository = feedRepository;
        this.objectMapper = objectMapper;
        this.googleApiService = googleApiService;
        this.photoService = photoService; // NEU
        this.gcsStorageService = gcsStorageService;
        this.photosBucketName = photosBucketName;
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
    public List<FeedPlaceDTO> generateHistoricalFeed(List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // Deine Logik für den adaptiven Radius bleibt unverändert
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

        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Object[]> rawResults = feedRepository.findPlacesWithPhotosMatchingUserHistory(
                    historyJson, adaptiveRadius
            );


            return rawResults.stream().map(row -> {
                String signedPhotoUrl = gcsStorageService.generateSignedUrl(
                        photosBucketName,
                        (String) row[5],  // cast to String to be safe
                        12,
                        TimeUnit.HOURS
                );

                return new FeedPlaceDTO(
                        (Long) row[0],               // id
                        (String) row[1],             // googlePlaceId
                        (String) row[2],             // name
                        signedPhotoUrl,              // coverImageUrl (use signed URL here)
                        toTimestamp(row[4]),         // coverImageDate (fix: signedPhotoUrl is URL, so use row[4] instead)
                        toTimestamp(row[6]),         // newestDate
                        ((Number) row[7]).longValue(), // photoCount
                        (String) row[3]              // address
                );
            }).toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }

    private static Timestamp toTimestamp(Object obj) {
        if (obj instanceof Instant instant) {
            return Timestamp.from(instant);
        } else if (obj instanceof Timestamp ts) {
            return ts;
        } else if (obj == null) {
            return null;
        }
        throw new IllegalArgumentException("Cannot convert to Timestamp: " + obj.getClass());
    }
}