package com.iris.backend.service;

import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.repository.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neuer, zentraler Service zur Erstellung aller "Public Gallery" Feeds.
 * Dieser Service ist verantwortlich für die Umwandlung von Entities
 * (GooglePlace, CustomPlace) in das vereinheitlichte GalleryFeedItemDTO.
 */
@Service
@Transactional(readOnly = true)
public class GalleryFeedService {

    private final CustomPlaceRepository customPlaceRepository;
    private final GooglePlaceRepository googlePlaceRepository;
    private final PhotoRepository photoRepository;
    private final GcsStorageService gcsStorageService;

    @Value("${gcs.bucket.photos.name}")
    private String photosBucketName;

    public GalleryFeedService(CustomPlaceRepository customPlaceRepository,
                              GooglePlaceRepository googlePlaceRepository,
                              PhotoRepository photoRepository,
                              GcsStorageService gcsStorageService) {
        this.customPlaceRepository = customPlaceRepository;
        this.googlePlaceRepository = googlePlaceRepository;
        this.photoRepository = photoRepository;
        this.gcsStorageService = gcsStorageService;
    }

    /**
     * Holt den "Trending Spots"-Feed.
     * (Implementiert Logik von CustomPlaceController.getTrendingSpots)
     */
    public List<GalleryFeedItemDTO> getTrendingSpots() {
        // 1. Hole alle Entities, die 'trending' sind
        List<CustomPlace> trendingPlaces = customPlaceRepository.findAllByIsTrendingTrueOrderByCreatedAtDesc();

        // 2. Wandle sie in das Feed-DTO um
        return trendingPlaces.stream()
                .map(this::mapToFeedItemDTO) // private Mapper-Methode nutzen
                .collect(Collectors.toList());
    }

    /**
     * Holt den "Meine erstellten Spots"-Feed.
     * (Implementiert Logik von CustomPlaceController.getMyCreatedSpots)
     */
    public List<GalleryFeedItemDTO> getMyCreatedSpots(User currentUser) {
        // 1. Hole alle Entities für den Creator
        List<CustomPlace> userPlaces = customPlaceRepository.findAllByCreatorOrderByCreatedAtDesc(currentUser);

        // 2. Wandle sie in das Feed-DTO um
        return userPlaces.stream()
                .map(this::mapToFeedItemDTO) // private Mapper-Methode nutzen
                .collect(Collectors.toList());
    }

    /**
     * Holt den "Entdeckte Spots"-Feed (POIs + Iris Spots).
     * (Implementiert Logik von PlaceController.getNearbyPlaces)
     */
    public List<GalleryFeedItemDTO> getDiscoveredSpots(double latitude, double longitude) {

        // --- 1. Aktive Google POIs in der Nähe finden ---
        // (Diese Logik war vorher im alten PlaceService / GoogleApiService)
        List<GooglePlace> nearbyGooglePlaces = googlePlaceRepository.findPlacesWithinRadius(latitude, longitude, 500); // z.B. 500m Radius

        List<GalleryFeedItemDTO> googleFeedItems = nearbyGooglePlaces.stream()
                .map(this::mapToFeedItemDTO) // Mapper für GooglePlace
                .filter(dto -> dto.photoCount() > 0) // Nur Orte mit Fotos anzeigen
                .collect(Collectors.toList());

        // --- 2. Aktive Custom Iris Spots in der Nähe finden ---
        List<CustomPlace> nearbyCustomPlaces = customPlaceRepository.findActivePlacesForUserLocation(latitude, longitude);

        List<GalleryFeedItemDTO> customFeedItems = nearbyCustomPlaces.stream()
                .map(this::mapToFeedItemDTO) // Mapper für CustomPlace
                .filter(dto -> dto.photoCount() > 0) // Nur Orte mit Fotos anzeigen
                .collect(Collectors.toList());

        // --- 3. Beide Listen zusammenführen und sortieren (z.B. nach neustem Foto) ---
        List<GalleryFeedItemDTO> combinedList = Stream.concat(googleFeedItems.stream(), customFeedItems.stream())
                .sorted(Comparator.comparing(GalleryFeedItemDTO::newestPhotoTimestamp).reversed())
                .collect(Collectors.toList());

        return combinedList;
    }


    // =================================================================================
    // Private Mapper-Methoden (Das Herzstück der DTO-Umwandlung)
    // =================================================================================

    /**
     * Private Hilfsmethode: Wandelt eine CustomPlace-Entity in ein GalleryFeedItemDTO um.
     * Führt DB-Abfragen für Foto-Infos aus.
     */
    private GalleryFeedItemDTO mapToFeedItemDTO(CustomPlace place) {
        // TODO: Diese Abfragen sind in einer Schleife nicht performant (N+1 Problem).
        // Später optimieren wir das mit einer einzigen Aggregat-Query.
        // Für jetzt ist es funktional.
        AggregatedPhotoInfo photoInfo = getAggregatedPhotoInfo(null, place.getId());

        return new GalleryFeedItemDTO(
                GalleryPlaceType.IRIS_SPOT,
                place.getName(),
                place.getLocation().getY(), // Latitude
                place.getLocation().getX(), // Longitude
                photoInfo.coverImageUrl(),
                photoInfo.photoCount(),
                photoInfo.newestPhotoTimestamp(),
                null, // googlePlaceId
                place.getId(), // customPlaceId
                null, // address
                place.getRadiusMeters(),
                place.getAccessType().name(),
                place.isTrending(),
                place.isLive(),
                place.getExpiresAt()
        );
    }

    /**
     * Private Hilfsmethode: Wandelt eine GooglePlace-Entity in ein GalleryFeedItemDTO um.
     */
    private GalleryFeedItemDTO mapToFeedItemDTO(GooglePlace place) {
        // TODO: Auch hier N+1 Problem, das wir später optimieren.
        AggregatedPhotoInfo photoInfo = getAggregatedPhotoInfo(place.getId(), null);

        return new GalleryFeedItemDTO(
                GalleryPlaceType.GOOGLE_POI,
                place.getName(),
                place.getLocation().getY(),
                place.getLocation().getX(),
                photoInfo.coverImageUrl(),
                photoInfo.photoCount(),
                photoInfo.newestPhotoTimestamp(),
                place.getId(), // googlePlaceId
                null, // customPlaceId
                place.getAddress(),
                place.getRadiusMeters(),
                "PUBLIC", // Google Places sind immer PUBLIC
                false,    // Google Places sind nie 'Trending'
                true,
                null      // Google Places laufen nicht ab
        );
    }

    /**
     * Holt Foto-Anzahl, Cover-URL und neustes Datum für einen Ort.
     * (Später optimieren wir das direkt in der Haupt-Query)
     */
    private record AggregatedPhotoInfo(long photoCount, String coverImageUrl, OffsetDateTime newestPhotoTimestamp) {}

    private AggregatedPhotoInfo getAggregatedPhotoInfo(Long googlePlaceId, UUID customPlaceId) {
        // Findet das neuste, aktive Public-Foto für das Cover-Bild
        Optional<Photo> coverPhotoOpt;
        long count;

        if (googlePlaceId != null) {
            coverPhotoOpt = photoRepository.findFirstByGooglePlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(googlePlaceId, PhotoVisibility.PUBLIC, OffsetDateTime.now());
            count = photoRepository.countByGooglePlaceIdAndVisibilityAndExpiresAtAfter(googlePlaceId, PhotoVisibility.PUBLIC, OffsetDateTime.now());
        } else {
            coverPhotoOpt = photoRepository.findFirstByCustomPlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(customPlaceId, PhotoVisibility.PUBLIC, OffsetDateTime.now());
            count = photoRepository.countByCustomPlaceIdAndVisibilityAndExpiresAtAfter(customPlaceId, PhotoVisibility.PUBLIC, OffsetDateTime.now());
        }

        if (coverPhotoOpt.isEmpty()) {
            return new AggregatedPhotoInfo(0, null, null);
        }

        Photo coverPhoto = coverPhotoOpt.get();
        String signedUrl = gcsStorageService.generateSignedUrl(
                photosBucketName,
                coverPhoto.getStorageUrl(), // Annahme: storageUrl ist nur der Objektname
                15,
                TimeUnit.MINUTES
        );

        return new AggregatedPhotoInfo(count, signedUrl, coverPhoto.getUploadedAt());
    }

    // Damit das funktioniert, müssen wir das PhotoRepository um diese Methoden erweitern:
    /*
    Im PhotoRepository.java hinzufügen:

    // Für Cover-Bild GooglePlace
    Optional<Photo> findFirstByGooglePlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(Long googlePlaceId, PhotoVisibility visibility, OffsetDateTime now);
    // Für Foto-Anzahl GooglePlace
    long countByGooglePlaceIdAndVisibilityAndExpiresAtAfter(Long googlePlaceId, PhotoVisibility visibility, OffsetDateTime now);

    // Für Cover-Bild CustomPlace
    Optional<Photo> findFirstByCustomPlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(UUID customPlaceId, PhotoVisibility visibility, OffsetDateTime now);
    // Für Foto-Anzahl CustomPlace
    long countByCustomPlaceIdAndVisibilityAndExpiresAtAfter(UUID customPlaceId, PhotoVisibility visibility, OffsetDateTime now);
    */
}