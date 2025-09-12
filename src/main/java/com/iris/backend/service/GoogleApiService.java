package com.iris.backend.service;

import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.repository.GooglePlaceRepository;
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
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiService.class);
    private final GeoApiContext geoApiContext;
    private final GooglePlaceRepository googlePlaceRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

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

    public GoogleApiService(GeoApiContext geoApiContext, GooglePlaceRepository googlePlaceRepository) {
        this.geoApiContext = geoApiContext;
        this.googlePlaceRepository = googlePlaceRepository;
    }

    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);
            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(50)
                    .await();

            return Arrays.stream(response.results)
                    .filter(googlePlace -> Collections.disjoint(Arrays.asList(googlePlace.types), UNINTERESTING_PLACE_TYPES))
                    .map(this::saveOrUpdatePlaceFromPoi)
                    .sorted(Comparator.comparing(PlaceDTO::importance).reversed()) // Wichtigste zuerst
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error calling Google Places API for nearby search: {}", e.getMessage());
            return List.of();
        }
    }

    private PlaceDTO saveOrUpdatePlaceFromPoi(PlacesSearchResult placeResult) {
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

        // KORREKTUR: Wir geben die neuen Felder im DTO zurück
        return new PlaceDTO(
                savedGooglePlace.getId(),
                savedGooglePlace.getGooglePlaceId(),
                savedGooglePlace.getName(),
                savedGooglePlace.getAddress(),
                null, // Fotos werden hier nicht geladen
                savedGooglePlace.getRadiusMeters(),
                savedGooglePlace.getImportance()
        );
    }
}