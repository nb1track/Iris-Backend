package com.iris.backend.service;

import com.iris.backend.dto.ParticipantDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.repository.PhotoRepository;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GoogleApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiService.class);
    private final GeoApiContext geoApiContext;
    private final GooglePlaceRepository googlePlaceRepository;
    private final PhotoRepository photoRepository;
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${gcs.bucket.profile-images.name}")
    private String profileImagesBucketName;

    // --- NEU: Regelwerk für Ortstypen ---
    private record PlaceRule(int radius, int importance) {}

    private static final Map<String, PlaceRule> PLACE_RULES = new HashMap<>();
    static {
        // Hohe Wichtigkeit, kleiner Radius
        PLACE_RULES.put("restaurant", new PlaceRule(50, 9));
        PLACE_RULES.put("cafe", new PlaceRule(40, 8));
        PLACE_RULES.put("bar", new PlaceRule(60, 8));
        PLACE_RULES.put("store", new PlaceRule(70, 7));
        PLACE_RULES.put("shopping_mall", new PlaceRule(200, 7));

        // Mittlere Wichtigkeit, mittlerer/großer Radius
        PLACE_RULES.put("park", new PlaceRule(300, 5));
        PLACE_RULES.put("tourist_attraction", new PlaceRule(150, 6));
        PLACE_RULES.put("museum", new PlaceRule(100, 6));

        // Niedrige Wichtigkeit
        PLACE_RULES.put("train_station", new PlaceRule(250, 4));
        PLACE_RULES.put("airport", new PlaceRule(1000, 3));
    }
    private static final PlaceRule DEFAULT_RULE = new PlaceRule(100, 0); // Standard für alles andere
    // --- ENDE Regelwerk ---

    private static final Set<String> UNINTERESTING_PLACE_TYPES = Set.of(
            "street_address", "route", "intersection", "political", "country",
            "administrative_area_level_1", "administrative_area_level_2",
            "locality", "sublocality", "postal_code", "plus_code", "doctor"
    );

    public GoogleApiService(GeoApiContext geoApiContext,
                            GooglePlaceRepository googlePlaceRepository,
                            PhotoRepository photoRepository,
                            FriendshipRepository friendshipRepository,
                            GcsStorageService gcsStorageService) {
        this.geoApiContext = geoApiContext;
        this.googlePlaceRepository = googlePlaceRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService;
    }

    /**
     * KORREKTUR: Gibt jetzt List<GalleryFeedItemDTO> zurück
     */
    public List<GalleryFeedItemDTO> findNearbyPlaces(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);
            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(50) // Radius für die API-Suche
                    .await();

            return Arrays.stream(response.results)
                    .filter(googlePlace -> Collections.disjoint(Arrays.asList(googlePlace.types), UNINTERESTING_PLACE_TYPES))
                    .map(this::saveOrUpdatePlaceFromPoi) // Gibt jetzt GalleryFeedItemDTO zurück
                    .sorted(Comparator.comparing(GalleryFeedItemDTO::name)) // Sortiere nach Name (passend zu getTaggablePlaces)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error calling Google Places API for nearby search: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * KORREKTUR: Gibt jetzt GalleryFeedItemDTO zurück
     */
    private GalleryFeedItemDTO saveOrUpdatePlaceFromPoi(PlacesSearchResult placeResult) {
        GooglePlace googlePlace = googlePlaceRepository.findByGooglePlaceId(placeResult.placeId).orElseGet(GooglePlace::new);
        googlePlace.setGooglePlaceId(placeResult.placeId);
        googlePlace.setName(placeResult.name);
        googlePlace.setAddress(placeResult.vicinity);
        googlePlace.setLocation(geometryFactory.createPoint(new Coordinate(placeResult.geometry.location.lng, placeResult.geometry.location.lat)));

        // --- NEU: Wende unser Regelwerk an ---
        PlaceRule rule = Arrays.stream(placeResult.types)
                .map(PLACE_RULES::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(DEFAULT_RULE);

        googlePlace.setRadiusMeters(rule.radius());
        googlePlace.setImportance(rule.importance());
        // --- ENDE Regelwerk-Anwendung ---

        GooglePlace savedGooglePlace = googlePlaceRepository.save(googlePlace);

        // KORREKTUR: Wir geben jetzt ein GalleryFeedItemDTO zurück
        return new GalleryFeedItemDTO(
                GalleryPlaceType.GOOGLE_POI,
                savedGooglePlace.getName(),
                savedGooglePlace.getLocation().getY(), // latitude
                savedGooglePlace.getLocation().getX(), // longitude
                null, // coverImageUrl - nicht benötigt für taggable places
                0,    // photoCount - nicht benötigt für taggable places
                null, // newestPhotoTimestamp - nicht benötigt für taggable places
                savedGooglePlace.getId(), // googlePlaceId (unsere interne Long ID)
                null, // customPlaceId
                savedGooglePlace.getAddress(),
                savedGooglePlace.getRadiusMeters(),
                null, // accessType
                false, // isTrending
                true,  // isLive (Google POIs sind immer "live")
                null   // expiresAt
        );
    }

    /**
     * NEU: Holt die Participants für einen Google Place.
     * Analoge Logik zu CustomPlaceService.getParticipants, aber ohne Owner-Check.
     */
    @Transactional(readOnly = true)
    public List<ParticipantDTO> getParticipants(Long placeId, User currentUser) {
        // 1. Prüfen, ob der Ort existiert (optional, aber sauberer)
        if (!googlePlaceRepository.existsById(placeId)) {
            throw new RuntimeException("Google Place not found with ID: " + placeId);
        }

        // 2. Teilnehmer finden (alle User, die dort Fotos hochgeladen haben)
        List<User> participants = photoRepository.findDistinctUploadersByGooglePlaceId(placeId);

        // 3. Freundschaften des aktuellen Users laden
        List<Friendship> friendships = friendshipRepository.findByUserOneAndStatusOrUserTwoAndStatus(
                currentUser, FriendshipStatus.ACCEPTED,
                currentUser, FriendshipStatus.ACCEPTED
        );

        Set<UUID> friendIds = friendships.stream()
                .map(f -> f.getUserOne().getId().equals(currentUser.getId()) ? f.getUserTwo().getId() : f.getUserOne().getId())
                .collect(Collectors.toSet());

        // 4. In DTOs umwandeln (mit signierter URL und Freundschafts-Status)
        return participants.stream()
                .map(user -> {
                    String signedProfileUrl = null;
                    String objectName = user.getProfileImageUrl();

                    if (objectName != null && !objectName.isBlank()) {
                        signedProfileUrl = gcsStorageService.generateSignedUrl(
                                profileImagesBucketName,
                                objectName,
                                15,
                                TimeUnit.MINUTES
                        );
                    }

                    boolean isFriend = friendIds.contains(user.getId());
                    if (user.getId().equals(currentUser.getId())) {
                        isFriend = false; // Man ist nicht mit sich selbst befreundet (für die UI Logik)
                    }

                    return new ParticipantDTO(user.getId(), user.getUsername(), signedProfileUrl, isFriend);
                })
                .collect(Collectors.toList());
    }
}