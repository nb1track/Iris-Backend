package com.iris.backend.service;

import com.iris.backend.dto.FeedPlaceDTO;
import com.iris.backend.dto.HistoricalPointDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.repository.FeedRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final ObjectMapper objectMapper;
    private final GcsStorageService gcsStorageService;
    private final String photosBucketName;

    public FeedService(
            FeedRepository feedRepository,
            ObjectMapper objectMapper,
            GcsStorageService gcsStorageService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName) {
        this.feedRepository = feedRepository;
        this.objectMapper = objectMapper;
        this.gcsStorageService = gcsStorageService;
        this.photosBucketName = photosBucketName;
    }

    /**
     * Generates a historical feed based on provided historical location points.
     * Merges and processes results from Google Places data and custom places data,
     * sorts them by their newest date in descending order, and converts them into
     * a list of FeedPlaceDTO objects.
     *
     * @param history a list of {@link HistoricalPointDTO} objects representing
     *                historical location points. Each point includes latitude,
     *                longitude, and timestamp. If the list is null or empty, an
     *                empty list is returned.
     * @return a list of {@link FeedPlaceDTO} objects representing the generated
     *         historical feed. If no results are found, an empty list is returned.
     *         Each object contains information about a place, such as its name,
     *         address, dates, and other attributes.
     * @throws RuntimeException if an error occurs during JSON processing.
     */
    @Transactional(readOnly = true)
    public List<FeedPlaceDTO> generateHistoricalFeed(List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        double adaptiveRadius = 300; // Vereinfacht für dieses Beispiel

        try {
            String historyJson = objectMapper.writeValueAsString(history);

            // 1. Rufe BEIDE Abfragen aus dem Repository auf
            List<Object[]> googleResults = feedRepository.findGooglePlacesMatchingHistory(historyJson, adaptiveRadius);
            List<Object[]> customResults = feedRepository.findCustomPlacesMatchingHistory(historyJson, adaptiveRadius);

            // 2. Wandle die Ergebnisse in DTOs um
            Stream<FeedPlaceDTO> googlePlacesStream = googleResults.stream().map(row -> new FeedPlaceDTO(
                    (Long) row[0], (String) row[1], (String) row[2],
                    generateSignedUrl((String) row[4]), toTimestamp(row[5]), toTimestamp(row[6]),
                    ((Number) row[7]).longValue(), (String) row[3],
                    "PUBLIC", false, true // Standardwerte für Google Places
            ));

            Stream<FeedPlaceDTO> customPlacesStream = customResults.stream().map(row -> new FeedPlaceDTO(
                    0L, "custom_" + row[0].toString(), (String) row[1], // Verwende eine Dummy-ID für Custom Places
                    generateSignedUrl((String) row[2]), toTimestamp(row[3]), toTimestamp(row[4]),
                    ((Number) row[5]).longValue(), "Custom Location",
                    (String) row[7], (Boolean) row[8], (Boolean) row[9] // Die neuen Felder
            ));

            // 3. Kombiniere und sortiere die Listen
            return Stream.concat(googlePlacesStream, customPlacesStream)
                    .sorted(Comparator.comparing(FeedPlaceDTO::newestDate).reversed())
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }

    // Hilfsmethoden bleiben unverändert
    private String generateSignedUrl(String objectName) {
        if (objectName == null) return null;
        return gcsStorageService.generateSignedUrl(photosBucketName, objectName, 12, TimeUnit.HOURS);
    }

    private static Timestamp toTimestamp(Object obj) {
        if (obj instanceof Instant instant) return Timestamp.from(instant);
        if (obj instanceof Timestamp ts) return ts;
        return null;
    }
}