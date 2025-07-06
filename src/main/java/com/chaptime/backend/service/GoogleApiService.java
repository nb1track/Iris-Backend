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
     * Attempts to find a nearby place based on the given geographical coordinates.
     * First, it uses the Places API to search for points of interest (POI) within
     * a specified radius. If a POI is found, its details are retrieved and saved
     * in the database if it does not already exist. If no POI is found, reverse
     * geocoding is performed to derive an approximate address at the given location.
     * The method then converts the found place or address into a PlaceDTO object
     * and returns it.
     *
     * @param latitude the latitude of the geographic location to search for places
     * @param longitude the longitude of the geographic location to search for places
     * @return an Optional containing a PlaceDTO with the details of the nearby place or
     *         an empty Optional if no place could be found
     */
    public Optional<PlaceDTO> findNearbyPlace(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);

            // Versuch 1: Finde einen POI
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(75)
                    .await();

            // Fall A: POI gefunden
            if (placesResponse.results.length > 0) {
                PlacesSearchResult topPlace = placesResponse.results[0];

                // Hole die genaue Adresse für den gefundenen POI
                GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, new LatLng(topPlace.geometry.location.lat, topPlace.geometry.location.lng)).await();
                String preciseAddress = (geocodingResults.length > 0) ? geocodingResults[0].formattedAddress : topPlace.vicinity;

                Place placeEntity = placeRepository.findByGooglePlaceId(topPlace.placeId).orElseGet(() -> {
                    Place newPlace = new Place();
                    newPlace.setGooglePlaceId(topPlace.placeId);
                    newPlace.setName(topPlace.name);
                    newPlace.setAddress(preciseAddress);
                    newPlace.setLocation(geometryFactory.createPoint(new Coordinate(topPlace.geometry.location.lng, topPlace.geometry.location.lat)));
                    return placeRepository.save(newPlace);
                });
                PlaceDTO placeDTO = new PlaceDTO(placeEntity.getId(), placeEntity.getGooglePlaceId(), placeEntity.getName(), placeEntity.getAddress());
                return Optional.of(placeDTO);
            }
            // Fall B: Kein POI gefunden, nutze Reverse Geocoding
            else {
                GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, coords).await();
                if (geocodingResults.length > 0) {
                    GeocodingResult topAddress = geocodingResults[0];
                    String placeId = topAddress.placeId;
                    String name = topAddress.formattedAddress.split(",")[0];

                    Place placeEntity = placeRepository.findByGooglePlaceId(placeId).orElseGet(() -> {
                        Place newPlace = new Place();
                        newPlace.setGooglePlaceId(placeId);
                        newPlace.setName(name);
                        newPlace.setAddress(topAddress.formattedAddress);
                        newPlace.setLocation(geometryFactory.createPoint(new Coordinate(longitude, latitude)));
                        return placeRepository.save(newPlace);
                    });
                    PlaceDTO placeDTO = new PlaceDTO(placeEntity.getId(), placeEntity.getGooglePlaceId(), placeEntity.getName(), placeEntity.getAddress());
                    return Optional.of(placeDTO);
                }
            }
        } catch (Exception e) {
            logger.error("Error calling Google APIs: {}", e.getMessage());
        }
        return Optional.empty();
    }
}