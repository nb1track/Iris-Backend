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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * (GooglePlace, CustomPlace) in das vereinheitlichte GalleryFeedItemDTO
 * und unterstützt alle Anwendungsfälle.
 */
@Service
@Transactional(readOnly = true)
public class GalleryFeedService {

    private static final Logger logger = LoggerFactory.getLogger(GalleryFeedService.class);
    private final CustomPlaceRepository customPlaceRepository;
    private final GooglePlaceRepository googlePlaceRepository;
    private final PhotoRepository photoRepository;
    private final GcsStorageService gcsStorageService;
    private final GoogleApiService googleApiService;

    @Value("${gcs.bucket.name}")
    private String photosBucketName;

    /**
     * Interner Record zur Bündelung von Foto-Aggregationsergebnissen.
     */
    private record AggregatedPhotoInfo(long count, String coverImageUrl, OffsetDateTime newestPhotoTimestamp) {
        /**
         * Statische Konstante für den schnellen "Taggable Places"-Feed,
         * der keine Foto-Infos benötigt.
         */
        public static final AggregatedPhotoInfo EMPTY = new AggregatedPhotoInfo(0, null, null);
    }

    public GalleryFeedService(CustomPlaceRepository customPlaceRepository,
                              GooglePlaceRepository googlePlaceRepository,
                              PhotoRepository photoRepository,
                              GcsStorageService gcsStorageService,
                              GoogleApiService googleApiService) {
        this.customPlaceRepository = customPlaceRepository;
        this.googlePlaceRepository = googlePlaceRepository;
        this.photoRepository = photoRepository;
        this.gcsStorageService = gcsStorageService;
        this.googleApiService = googleApiService;
    }

    /**
     * Holt den "Entdeckt"-Feed (deine "irisShareSpots"-Page).
     * Zeigt nur Orte an, die aktive, öffentliche Fotos enthalten.
     */
    public List<GalleryFeedItemDTO> getDiscoverFeed(double latitude, double longitude) {
        List<GooglePlace> googlePlaces = googlePlaceRepository.findActivePlacesForUserLocation(latitude, longitude);
        List<CustomPlace> customPlaces = customPlaceRepository.findActivePlacesForUserLocation(latitude, longitude);

        Stream<GalleryFeedItemDTO> combinedStream = Stream.concat(
                googlePlaces.stream().map(place -> convertToFeedItem(place, true)), // true = Foto-Infos laden
                customPlaces.stream().map(place -> convertToFeedItem(place, true))  // true = Foto-Infos laden
        );

        Comparator<GalleryFeedItemDTO> comparator = Comparator.comparing(GalleryFeedItemDTO::name);

        return combinedStream
                .filter(item -> item.photoCount() > 0)
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /**
     * Holt alle Orte (Google POIs und Iris Spots) in der Nähe eines Benutzers,
     * die zum Taggen eines Fotos verfügbar sind (deine "cameraPage").
     *
     * KORREKTUR: Diese Methode ruft jetzt den GoogleApiService, wenn
     * keine lokalen Google Places gefunden werden.
     */
    @Transactional(readOnly = false)
    public List<GalleryFeedItemDTO> getTaggablePlaces(double latitude, double longitude) {
        // [1] Hole zuerst Custom Places (Iris Spots) aus der lokalen DB
        List<CustomPlace> customPlaces = customPlaceRepository.findActivePlacesForUserLocation(latitude, longitude);

        // [2] Versuche, Google Places aus der lokalen DB zu holen
        List<GooglePlace> localGooglePlaces = googlePlaceRepository.findActivePlacesForUserLocation(latitude, longitude);

        List<GalleryFeedItemDTO> googlePlaceDTOs;

        if (localGooglePlaces.isEmpty()) {
            // [3a] Wenn keine lokalen Google Places gefunden, rufe die Google API auf
            // Diese Methode (findNearbyPlaces) gibt jetzt List<GalleryFeedItemDTO> zurück
            // und speichert die Ergebnisse bereits in der DB für zukünftige Suchen.
            logger.info("Keine lokalen Google Places gefunden. Rufe Google API für lat={}, lon={}", latitude, longitude);
            googlePlaceDTOs = googleApiService.findNearbyPlaces(latitude, longitude);
        } else {
            // [3b] Wenn lokale Google Places gefunden wurden, konvertiere sie in DTOs
            // (ohne Foto-Infos, da 'false')
            googlePlaceDTOs = localGooglePlaces.stream()
                    .map(place -> convertToFeedItem(place, false))
                    .collect(Collectors.toList());
        }

        // [4] Kombiniere die DTOs der Custom Places und der Google Places
        Stream<GalleryFeedItemDTO> combinedStream = Stream.concat(
                googlePlaceDTOs.stream(),
                customPlaces.stream().map(place -> convertToFeedItem(place, false))  // false = KEINE Foto-Infos laden
        );

        // [5] Sortiere die kombinierte Liste nach Namen und gib sie zurück
        return combinedStream
                .sorted(Comparator.comparing(GalleryFeedItemDTO::name))
                .collect(Collectors.toList());
    }

    /**
     * Holt alle "Trending" Iris Spots.
     * (Implementiert die Logik für CustomPlaceController)
     */
    public List<GalleryFeedItemDTO> getTrendingSpots() {
        return customPlaceRepository.findAllByIsTrendingTrueOrderByCreatedAtDesc()
                .stream()
                .map(place -> convertToFeedItem(place, true)) // true = Foto-Infos laden
                .filter(item -> item.photoCount() > 0) // Annahme: Trending Spots sollen auch nur angezeigt werden, wenn sie Fotos haben
                .collect(Collectors.toList());
    }

    /**
     * Holt alle vom eingeloggten User erstellten Iris Spots.
     * (Implementiert die Logik für CustomPlaceController)
     */
    public List<GalleryFeedItemDTO> getMyCreatedSpots(User currentUser) {
        return customPlaceRepository.findAllByCreatorOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(place -> convertToFeedItem(place, true)) // true = Foto-Infos laden
                .collect(Collectors.toList());
    }

    /**
     * Private Helfermethode: Konvertiert ein GooglePlace-Entity in ein DTO.
     */
    private GalleryFeedItemDTO convertToFeedItem(GooglePlace place, boolean loadPhotoInfo) {
        AggregatedPhotoInfo photoInfo = loadPhotoInfo
                ? getAggregatedPhotoInfo(place.getId(), null)
                : AggregatedPhotoInfo.EMPTY;

        return new GalleryFeedItemDTO(
                GalleryPlaceType.GOOGLE_POI,
                place.getName(),
                place.getLocation().getY(),
                place.getLocation().getX(),
                photoInfo.coverImageUrl(),
                photoInfo.count(),
                photoInfo.newestPhotoTimestamp(),
                place.getId(),
                null,
                place.getAddress(),
                place.getRadiusMeters(),
                null,
                false,
                true,
                null
        );
    }

    /**
     * Private Helfermethode: Konvertiert ein CustomPlace-Entity in ein DTO.
     */
    private GalleryFeedItemDTO convertToFeedItem(CustomPlace place, boolean loadPhotoInfo) {
        AggregatedPhotoInfo photoInfo = loadPhotoInfo
                ? getAggregatedPhotoInfo(null, place.getId())
                : AggregatedPhotoInfo.EMPTY;

        return new GalleryFeedItemDTO(
                GalleryPlaceType.IRIS_SPOT,
                place.getName(),
                place.getLocation().getY(),
                place.getLocation().getX(),
                photoInfo.coverImageUrl(),
                photoInfo.count(),
                photoInfo.newestPhotoTimestamp(),
                null,
                place.getId(),
                null,
                place.getRadiusMeters(),
                place.getAccessType().name(),
                place.isTrending(),
                place.isLive(),
                place.getExpiresAt()
        );
    }

    /**
     * Holt die aggregierten Foto-Infos (Anzahl, Cover-URL) für einen Ort.
     */
    private AggregatedPhotoInfo getAggregatedPhotoInfo(Long googlePlaceId, UUID customPlaceId) {
        Optional<Photo> coverPhotoOpt;
        long count;
        OffsetDateTime now = OffsetDateTime.now();

        if (googlePlaceId != null) {
            coverPhotoOpt = photoRepository.findFirstByGooglePlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                    googlePlaceId, PhotoVisibility.PUBLIC, now
            );
            count = photoRepository.countByGooglePlaceIdAndVisibilityAndExpiresAtAfter(
                    googlePlaceId, PhotoVisibility.PUBLIC, now
            );
        } else {
            coverPhotoOpt = photoRepository.findFirstByCustomPlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                    customPlaceId, PhotoVisibility.PUBLIC, now
            );
            count = photoRepository.countByCustomPlaceIdAndVisibilityAndExpiresAtAfter(
                    customPlaceId, PhotoVisibility.PUBLIC, now
            );
        }

        if (coverPhotoOpt.isEmpty()) {
            return AggregatedPhotoInfo.EMPTY;
        }

        Photo coverPhoto = coverPhotoOpt.get();

        // Annahme: storageUrl ist nur der Objektname, nicht die volle URL
        String objectName = coverPhoto.getStorageUrl();
        if (coverPhoto.getStorageUrl().contains("/")) {
            objectName = coverPhoto.getStorageUrl().substring(coverPhoto.getStorageUrl().lastIndexOf('/') + 1);
        }

        // !! HINWEIS: DIES WIRD FEHLSCHLAGEN, BIS WIR SCHRITT 2 MACHEN !!
        String signedUrl = gcsStorageService.generateSignedUrl(
                photosBucketName,
                objectName,
                15,
                TimeUnit.MINUTES
        );

        return new AggregatedPhotoInfo(count, signedUrl, coverPhoto.getUploadedAt());
    }

    /**
     * NEU: Öffentliche Methode, um ein einzelnes CustomPlace-Entity
     * in ein GalleryFeedItemDTO umzuwandeln.
     * Nützlich nach dem Erstellen/Aktualisieren eines Spots.
     *
     * @param place Das entity, das konvertiert werden soll.
     * @param loadPhotoInfo 'true', wenn Foto-Infos geladen werden sollen.
     * @return Das konvertierte GalleryFeedItemDTO.
     */
    public GalleryFeedItemDTO getFeedItemForPlace(CustomPlace place, boolean loadPhotoInfo) {
        return convertToFeedItem(place, loadPhotoInfo);
    }
}