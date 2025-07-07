package com.chaptime.backend.service;

import com.chaptime.backend.model.Place;
import com.chaptime.backend.repository.PlaceRepository;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.model.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.chaptime.backend.dto.PlaceDTO;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GoogleApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiService.class);
    private final GeoApiContext geoApiContext;
    private final PlaceRepository placeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Constructs a new instance of GoogleApiService.
     *
     * @param geoApiContext the GeoApiContext used to interact with the Google Maps API
     * @param placeRepository the repository for managing Place entities in the database
     */
    public GoogleApiService(GeoApiContext geoApiContext, PlaceRepository placeRepository) {
        this.geoApiContext = geoApiContext;
        this.placeRepository = placeRepository;
    }

    /**
     * Finds nearby places within a specific radius around the given geographic coordinates and returns
     * a list of corresponding place data transfer objects (DTOs).
     *
     * This method queries the Google Places API to identify places of interest near the specified
     * latitude and longitude. The results are converted into PlaceDTO instances, and details are
     * saved or updated in the database.
     *
     * @param latitude the latitude of the location to search nearby places
     * @param longitude the longitude of the location to search nearby places
     * @return a list of PlaceDTO objects representing nearby places. If an error occurs during the
     *         API call or processing, an empty list is returned.
     */
// Ersetze die existierende findNearbyPlace-Methode hiermit
    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);

            // Suche nach interessanten Orten in einem 25-Meter-Radius
            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(25) // Dein gewünschter Radius
                    .await();

            // Wandle alle Google-Ergebnisse in unsere eigenen Place-Objekte (und DTOs) um
            return Arrays.stream(response.results)
                    .map(googlePlace -> {
                        // Speichere oder aktualisiere jeden gefundenen Ort in unserer DB
                        Place place = placeRepository.findByGooglePlaceId(googlePlace.placeId).orElseGet(Place::new);

                        place.setGooglePlaceId(googlePlace.placeId);
                        place.setName(googlePlace.name);
                        place.setAddress(googlePlace.vicinity);
                        place.setLocation(geometryFactory.createPoint(new Coordinate(googlePlace.geometry.location.lng, googlePlace.geometry.location.lat)));

                        Place savedPlace = placeRepository.save(place);

                        // Gib ein DTO mit den wichtigsten Infos zurück
                        return new PlaceDTO(savedPlace.getId(),
                                savedPlace.getGooglePlaceId(),
                                savedPlace.getName(),
                                savedPlace.getAddress(),
                                null);
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error calling Google Places API for nearby search: {}", e.getMessage());
            return List.of(); // Gib im Fehlerfall eine leere Liste zurück
        }
    }
}