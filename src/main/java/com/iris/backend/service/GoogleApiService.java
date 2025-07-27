package com.iris.backend.service;

import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.Place;
import com.iris.backend.repository.PlaceRepository;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GoogleApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleApiService.class);
    private final GeoApiContext geoApiContext;
    private final PlaceRepository placeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public GoogleApiService(GeoApiContext geoApiContext, PlaceRepository placeRepository) {
        this.geoApiContext = geoApiContext;
        this.placeRepository = placeRepository;
    }

    /**
     * Retrieves a list of nearby places based on the provided geographic coordinates.
     * The method performs reverse geocoding and nearby places search using the Google API,
     * processes the results, and returns a consolidated list of places.
     *
     * @param latitude the latitude of the location to search nearby places
     * @param longitude the longitude of the location to search nearby places
     * @return a list of PlaceDTO objects representing the nearby places, ensuring unique entries
     */
    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        Map<String, PlaceDTO> placesMap = new LinkedHashMap<>();
        LatLng coords = new LatLng(latitude, longitude);

        double adaptiveRadius = determineAdaptiveRadius(coords);
        logger.info("Using adaptive radius of {}m for Places API search.", adaptiveRadius);

        try {
            GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, coords).await();
            findBestGeocodingResult(geocodingResults)
                    .ifPresent(bestResult -> {
                        // KORREKTUR: Eindeutigen Methodennamen verwenden
                        PlaceDTO precisePlace = saveOrUpdatePlaceFromGeocoding(bestResult, coords);
                        placesMap.put(precisePlace.googlePlaceId(), precisePlace);
                    });
        } catch (Exception e) {
            logger.error("Error calling Google Geocoding API: {}", e.getMessage());
        }

        try {
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius((int) adaptiveRadius)
                    .rankby(RankBy.PROMINENCE)
                    .await();

            Arrays.stream(placesResponse.results)
                    // KORREKTUR: Eindeutigen Methodennamen verwenden
                    .map(this::saveOrUpdatePlaceFromPoi)
                    .forEach(placeDto -> placesMap.putIfAbsent(placeDto.googlePlaceId(), placeDto));

        } catch (Exception e) {
            logger.error("Error calling Google Places API: {}", e.getMessage());
        }

        return new ArrayList<>(placesMap.values().stream()
                .collect(Collectors.toMap(PlaceDTO::name, Function.identity(), (e1, e2) -> e1, LinkedHashMap::new))
                .values());
    }

    /**
     * Determines an adaptive search radius based on the density of nearby places at the given coordinates.
     * This*/
    private double determineAdaptiveRadius(LatLng coords) {
        try {
            PlacesSearchResponse densityCheck = PlacesApi.nearbySearchQuery(geoApiContext, coords).radius(1000).await();
            long placeCount = Arrays.stream(densityCheck.results).count();

            if (placeCount > 15) return 25.0; // Sehr dichte Stadt: 25m Radius
            if (placeCount > 5) return 50.0; // Vorort/Kleinstadt: 50m Radius
            return 300.0; // Ländlich: 300m Radius
        } catch (Exception e) {
            logger.warn("Could not perform density check for adaptive radius. Falling back to default. Error: {}", e.getMessage());
            return 200.0;
        }
    }

    /**
     * Finds the best geocoding result from an array of GeocodingResult objects.
     * The method prioritizes results that contain the address type STREET_ADDRESS.
     * If no such result is found, the first result in the array is returned.
     *
     * @param results an array of GeocodingResult objects to evaluate; can be null or empty
     * @return an Optional containing the best matching GeocodingResult, or an empty Optional if the input array is null or empty
     */
    private Optional<GeocodingResult> findBestGeocodingResult(GeocodingResult[] results) {
        if (results == null || results.length == 0) return Optional.empty();
        return Arrays.stream(results)
                .filter(r -> Arrays.asList(r.types).contains(AddressType.STREET_ADDRESS))
                .findFirst()
                .or(() -> Optional.of(results[0]));
    }

    /**
     * Saves or updates place information obtained from the given geocoding result and coordinates.
     * If a place with the given Google Place ID already exists in the repository, it will be updated.
     * Otherwise, a new place will be created and saved.
     *
     * @param geocodingResult the geocoding result containing information about the place
     * @param coords the geographical coordinates of the place
     * @return a PlaceDTO object representing the saved or updated place
     */
    private PlaceDTO saveOrUpdatePlaceFromGeocoding(GeocodingResult geocodingResult, LatLng coords) {
        String placeId = geocodingResult.placeId;
        Place place = placeRepository.findByGooglePlaceId(placeId).orElseGet(Place::new);
        place.setGooglePlaceId(placeId);
        place.setName(cleanAddressName(geocodingResult.formattedAddress));
        place.setAddress(extractCityFromGeocoding(geocodingResult.addressComponents));
        place.setLocation(geometryFactory.createPoint(new Coordinate(coords.lng, coords.lat)));
        Place savedPlace = placeRepository.save(place);
        return new PlaceDTO(savedPlace.getId(), savedPlace.getGooglePlaceId(), savedPlace.getName(), savedPlace.getAddress(), null);
    }

    /**
     * Saves or updates a place in the repository using information from the provided POI (Place of Interest) data.
     * If a place with the given Google Place ID already exists, it will be updated. Otherwise, a new place is created and saved.
     *
     * @param placeResult the PlacesSearchResult object containing information about the place
     * @return a PlaceDTO object representing the saved or updated place
     */
    private PlaceDTO saveOrUpdatePlaceFromPoi(PlacesSearchResult placeResult) {
        Place place = placeRepository.findByGooglePlaceId(placeResult.placeId).orElseGet(Place::new);
        place.setGooglePlaceId(placeResult.placeId);
        place.setName(placeResult.name);
        String address = placeResult.vicinity;
        if (address != null && address.contains("+")) {
            address = null;
        }
        place.setAddress(address);
        place.setLocation(geometryFactory.createPoint(new Coordinate(placeResult.geometry.location.lng, placeResult.geometry.location.lat)));
        Place savedPlace = placeRepository.save(place);
        return new PlaceDTO(savedPlace.getId(), savedPlace.getGooglePlaceId(), savedPlace.getName(), savedPlace.getAddress(), null);
    }

    /**
     * Extracts the city name from an array of AddressComponent objects by locating the component
     * with the AddressComponentType of LOCALITY and returning its long name.
     *
     * @param components an*/
    private String extractCityFromGeocoding(AddressComponent[] components) {
        return Arrays.stream(components)
                .filter(c -> Arrays.asList(c.types).contains(AddressComponentType.LOCALITY))
                .map(c -> c.longName)
                .findFirst()
                .orElse(null);
    }

    /**
     * Cleans the given full address string by extracting and returning the name part
     * before the first comma. If the input string is null, the method returns null.
     *
     * @param fullAddress the full address string to be cleaned; may be null
     * @return the cleaned address name (substring before the first comma),
     *         or null if the input string is null
     */
    private String cleanAddressName(String fullAddress) {
        if (fullAddress == null) return null;
        return fullAddress.split(",")[0];
    }
}