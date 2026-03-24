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

    @Value("${gcs.bucket.photos.name}")
    private String photosBucketName;

    public record AggregatedPhotoInfo(long count, String coverImageUrl, OffsetDateTime newestPhotoTimestamp) {
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


    @Transactional(readOnly = false)
    public List<GalleryFeedItemDTO> getTaggablePlaces(double latitude, double longitude) {
        List<CustomPlace> customPlaces = customPlaceRepository.findActivePlacesForUserLocation(latitude, longitude);
        List<GooglePlace> localGooglePlaces = googlePlaceRepository.findActivePlacesForUserLocation(latitude, longitude);
        List<GalleryFeedItemDTO> googlePlaceDTOs;

        if (localGooglePlaces.isEmpty()) {
            logger.info("Keine lokalen Google Places gefunden. Rufe Google API für lat={}, lon={}", latitude, longitude);
            googlePlaceDTOs = googleApiService.findNearbyPlaces(latitude, longitude);
        } else {
            googlePlaceDTOs = localGooglePlaces.stream()
                    .map(place -> convertToFeedItem(place, false))
                    .collect(Collectors.toList());
        }

        Stream<GalleryFeedItemDTO> combinedStream = Stream.concat(
                googlePlaceDTOs.stream(),
                customPlaces.stream().map(place -> convertToFeedItem(place, false,false))
        );

        return combinedStream
                .sorted(Comparator.comparing(GalleryFeedItemDTO::name))
                .collect(Collectors.toList());
    }

    public List<GalleryFeedItemDTO> getTrendingSpots() {
        return customPlaceRepository.findAllByIsTrendingTrueOrderByCreatedAtDesc()
                .stream()
                .map(place -> convertToFeedItem(place, true, false))
                .filter(item -> item.photoCount() > 0)
                .collect(Collectors.toList());
    }

    public List<GalleryFeedItemDTO> getMyCreatedSpots(User currentUser) {
        return customPlaceRepository.findAllByCreatorOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(place -> convertToFeedItem(place, true, true))
                .collect(Collectors.toList());
    }

    private GalleryFeedItemDTO convertToFeedItem(GooglePlace place, boolean loadPhotoInfo) {
        AggregatedPhotoInfo photoInfo = loadPhotoInfo
                ? getAggregatedPhotoInfo(place.getId(), null, false)
                : AggregatedPhotoInfo.EMPTY;

        // NEU: Zähle Uploader für Google Place
        long participantCount = photoRepository.countDistinctUploadersByGooglePlaceId(place.getId());

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
                null,
                participantCount // Hier setzen wir jetzt den echten Wert statt 0L
        );
    }

    private GalleryFeedItemDTO convertToFeedItem(CustomPlace place, boolean loadPhotoInfo, boolean isOwnerMode) {
        String coverUrl = null;
        long photoCount = 0;
        OffsetDateTime newestTimestamp = null;

        if (place.getCoverImageUrl() != null && !place.getCoverImageUrl().isBlank()) {
            coverUrl = gcsStorageService.generateSignedUrl(
                    photosBucketName,
                    place.getCoverImageUrl(),
                    60,
                    TimeUnit.MINUTES
            );
        }

        if (loadPhotoInfo) {
            AggregatedPhotoInfo photoInfo = getAggregatedPhotoInfo(null, place.getId(), isOwnerMode);
            photoCount = photoInfo.count();
            newestTimestamp = photoInfo.newestPhotoTimestamp();

            if (coverUrl == null) {
                coverUrl = photoInfo.coverImageUrl();
            }
        }

        // NEU: Zähle Uploader für Custom Place (Nutzt die PhotoRepository Methode)
        long participantCount = photoRepository.countDistinctUploadersByCustomPlaceId(place.getId());

        return new GalleryFeedItemDTO(
                GalleryPlaceType.IRIS_SPOT,
                place.getName(),
                place.getLocation().getY(),
                place.getLocation().getX(),
                coverUrl,
                photoCount,
                newestTimestamp,
                null,
                place.getId(),
                null,
                place.getRadiusMeters(),
                place.getAccessType().name(),
                place.isTrending(),
                place.isLive(),
                place.getExpiresAt(),
                participantCount
        );
    }

    private AggregatedPhotoInfo getAggregatedPhotoInfo(Long googlePlaceId, UUID customPlaceId, boolean includePrivate) {
        long count;
        OffsetDateTime now = OffsetDateTime.now();

        if (googlePlaceId != null) {
            count = photoRepository.countByGooglePlaceIdAndVisibilityAndExpiresAtAfter(
                    googlePlaceId, PhotoVisibility.PUBLIC, now
            );
        } else {
            if (includePrivate) {
                count = photoRepository.countByCustomPlaceIdAndExpiresAtAfter(customPlaceId, now);
            } else {
                count = photoRepository.countByCustomPlaceIdAndVisibilityAndExpiresAtAfter(
                        customPlaceId, PhotoVisibility.PUBLIC, now
                );
            }
        }

        if (count == 0) {
            return AggregatedPhotoInfo.EMPTY;
        }

        Optional<Photo> coverPhotoOpt;
        if (googlePlaceId != null) {
            coverPhotoOpt = photoRepository.findFirstByGooglePlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                    googlePlaceId, PhotoVisibility.PUBLIC, now);
        } else {
            coverPhotoOpt = photoRepository.findFirstByCustomPlaceIdAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                    customPlaceId, PhotoVisibility.PUBLIC, now);
        }

        String signedUrl = null;
        OffsetDateTime uploadedAt = null;

        if (coverPhotoOpt.isPresent()) {
            Photo coverPhoto = coverPhotoOpt.get();
            uploadedAt = coverPhoto.getUploadedAt();
            String objectName = coverPhoto.getStorageUrl();
            if (coverPhoto.getStorageUrl().contains("/")) {
                objectName = coverPhoto.getStorageUrl().substring(coverPhoto.getStorageUrl().lastIndexOf('/') + 1);
            }
            signedUrl = gcsStorageService.generateSignedUrl(photosBucketName, objectName, 15, TimeUnit.MINUTES);
        }

        return new AggregatedPhotoInfo(count, signedUrl, uploadedAt);
    }

    public GalleryFeedItemDTO getFeedItemForPlace(CustomPlace place, boolean loadPhotoInfo) {
        return convertToFeedItem(place, loadPhotoInfo, true);
    }

    public GalleryFeedItemDTO getFeedItemForPlace(GooglePlace place, boolean loadPhotoInfo) {
        return convertToFeedItem(place, loadPhotoInfo);
    }
}