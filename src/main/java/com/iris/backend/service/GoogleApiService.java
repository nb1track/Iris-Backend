package com.iris.backend.service;

import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.repository.GooglePlaceRepository;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResponse;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoogleApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiService.class);
    private final GeoApiContext geoApiContext;
    private final GooglePlaceRepository googlePlaceRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // Die "schwarze Liste" von uninteressanten Ortstypen, die wir ignorieren.
    private static final Set<String> UNINTERESTING_PLACE_TYPES = Set.of(
            "street_address", "route", "intersection", "political", "country",
            "administrative_area_level_1", "administrative_area_level_2",
            "locality", "sublocality", "postal_code", "plus_code", "doctor"
    );

    public GoogleApiService(GeoApiContext geoApiContext, GooglePlaceRepository googlePlaceRepository) {
        this.geoApiContext = geoApiContext;
        this.googlePlaceRepository = googlePlaceRepository;
    }

    /**
     * Finds nearby Points of Interest (POIs) using the Google Places API.
     * Results are filtered to exclude simple addresses.
     */
    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);
            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(50) // Ein sinnvoller Radius für die POI-Suche
                    .await();

            return Arrays.stream(response.results)
                    .filter(googlePlace ->
                            // Filtert alle reinen Adressen heraus
                            Collections.disjoint(Arrays.asList(googlePlace.types), UNINTERESTING_PLACE_TYPES)
                    )
                    .map(this::saveOrUpdatePlaceFromPoi) // Wiederverwendung der Speicherlogik
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error calling Google Places API for nearby search: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Saves or updates a place based on a Places API result.
     */
    private PlaceDTO saveOrUpdatePlaceFromPoi(com.google.maps.model.PlacesSearchResult placeResult) {
        GooglePlace googlePlace = googlePlaceRepository.findByGooglePlaceId(placeResult.placeId).orElseGet(GooglePlace::new);
        googlePlace.setGooglePlaceId(placeResult.placeId);
        googlePlace.setName(placeResult.name);
        googlePlace.setAddress(placeResult.vicinity); // 'vicinity' ist oft eine nützliche Kurzbeschreibung
        googlePlace.setLocation(geometryFactory.createPoint(new Coordinate(placeResult.geometry.location.lng, placeResult.geometry.location.lat)));

        GooglePlace savedGooglePlace = googlePlaceRepository.save(googlePlace);

        return new PlaceDTO(savedGooglePlace.getId(), savedGooglePlace.getGooglePlaceId(), savedGooglePlace.getName(), savedGooglePlace.getAddress(), null);
    }
}