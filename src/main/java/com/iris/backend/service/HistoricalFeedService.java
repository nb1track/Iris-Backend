package com.iris.backend.service;

import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO; // NEUER IMPORT
// import com.iris.backend.dto.FeedPlaceDTO; // ALTER IMPORT ENTFERNT
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.repository.HistoricalFeedRepository; // NEUER IMPORT
// import com.iris.backend.repository.FeedRepository; // ALTER IMPORT ENTFERNT
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp; // Nicht mehr benötigt
import java.time.Instant; // Nicht mehr benötigt
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator; // Nicht mehr benötigt
import java.util.List;
import java.util.UUID; // Nicht mehr benötigt
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream; // Nicht mehr benötigt

@Service
// Umbenannt von FeedService zu HistoricalFeedService
public class HistoricalFeedService {

    private final HistoricalFeedRepository historicalFeedRepository; // NEU
    private final ObjectMapper objectMapper;
    private final GcsStorageService gcsStorageService;
    private final String photosBucketName;

    public HistoricalFeedService(
            HistoricalFeedRepository historicalFeedRepository, // NEU
            ObjectMapper objectMapper,
            GcsStorageService gcsStorageService,
            @Value("${gcs.bucket.photos.name}") String photosBucketName) {
        this.historicalFeedRepository = historicalFeedRepository;
        this.objectMapper = objectMapper;
        this.gcsStorageService = gcsStorageService;
        this.photosBucketName = photosBucketName;
    }

    /**
     * Generiert den Historical Feed.
     * Nutzt jetzt die neue, saubere Repository-Methode.
     */
    @Transactional(readOnly = true)
    public List<GalleryFeedItemDTO> generateHistoricalFeed(List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        try {
            String historyJson = objectMapper.writeValueAsString(history);

            // 1. Rufe die neue, saubere Query auf
            List<HistoricalFeedRepository.GalleryFeedItemDTOProjection> results =
                    historicalFeedRepository.findHistoricalFeed(historyJson);

            // 2. Wandle Projektionen in DTOs um und generiere signierte URLs
            return results.stream()
                    .map(projection -> {
                        String signedUrl = generateSignedUrl(projection.getCoverImageUrl());

                        OffsetDateTime newestPhotoTs = null;
                        if (projection.getNewestPhotoTimestamp() != null) {
                            // Wandle den Instant in ein OffsetDateTime (mit UTC als Offset)
                            newestPhotoTs = projection.getNewestPhotoTimestamp().atOffset(ZoneOffset.UTC);
                        }
                        // Erstelle das finale DTO
                        return new GalleryFeedItemDTO(
                                projection.getPlaceType(),
                                projection.getName(),
                                projection.getLatitude(),
                                projection.getLongitude(),
                                signedUrl, // Ersetze Objektname durch signierte URL
                                projection.getPhotoCount(),
                                newestPhotoTs,
                                projection.getGooglePlaceId(),
                                projection.getCustomPlaceId(),
                                projection.getAddress(),
                                projection.getRadiusMeters(),
                                projection.getAccessType(),
                                projection.getIsTrending(),
                                projection.getIsLive(),
                                projection.getExpiresAt()
                        );
                    })
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }

    // Hilfsmethode (unverändert)
    private String generateSignedUrl(String objectName) {
        if (objectName == null) return null;
        // Annahme: objectName ist der GCS-Objektname, nicht die volle URL
        return gcsStorageService.generateSignedUrl(photosBucketName, objectName, 12, TimeUnit.HOURS);
    }
}