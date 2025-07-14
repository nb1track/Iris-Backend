package com.chaptime.backend.service;

import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.repository.PlaceRepository;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.model.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;

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

    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        Map<String, PlaceDTO> placesMap = new LinkedHashMap<>();
        LatLng coords = new LatLng(latitude, longitude);
        PlaceDTO preciseAddressDto = null;
        String preciseStreetName = "";

        // --- STUFE 1: Exakte Adresse holen ---
        try {
            GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, coords).await();
            Optional<GeocodingResult> bestAddress = findBestGeocodingResult(geocodingResults);

            if (bestAddress.isPresent()) {
                preciseAddressDto = saveOrUpdatePlace(bestAddress.get(), coords);
                placesMap.put(preciseAddressDto.googlePlaceId(), preciseAddressDto);
                // Extrahiere den reinen Strassennamen für den späteren Vergleich
                preciseStreetName = getStreetNameFromAddress(preciseAddressDto.name());
                logger.info("Found precise address via Geocoding: {}. Using street name for filtering: {}", preciseAddressDto.name(), preciseStreetName);
            }
        } catch (Exception e) {
            logger.error("Error calling Google Geocoding API: {}", e.getMessage());
        }

        // --- STUFE 2: POIs in der Nähe holen ---
        try {
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(50).rankby(RankBy.PROMINENCE).await();

            final String finalPreciseStreetName = preciseStreetName;

            Arrays.stream(placesResponse.results)
                    .map(this::saveOrUpdatePlaceFromPoi)
                    // --- NEUE, STRENGERE FILTERLOGIK ---
                    .filter(poiDto -> {
                        boolean isAlreadyPresent = placesMap.containsKey(poiDto.googlePlaceId());
                        // Verwerfe den POI, wenn der Name den exakten Strassennamen enthält
                        boolean isSimilarAddress = !finalPreciseStreetName.isEmpty() && poiDto.name().contains(finalPreciseStreetName);
                        if (isSimilarAddress) {
                            logger.info("Filtering out similar POI '{}' because it contains street name '{}'", poiDto.name(), finalPreciseStreetName);
                        }
                        return !isAlreadyPresent && !isSimilarAddress;
                    })
                    .forEach(placeDto -> placesMap.put(placeDto.googlePlaceId(), placeDto));

        } catch (Exception e) {
            logger.error("Error calling Google Places API: {}", e.getMessage());
        }

        return new ArrayList<>(placesMap.values());
    }

    // --- Private Helper-Methoden ---

    private Optional<GeocodingResult> findBestGeocodingResult(GeocodingResult[] results) {
        if (results == null || results.length == 0) return Optional.empty();
        return Arrays.stream(results)
                .filter(r -> Arrays.asList(r.types).contains(AddressType.STREET_ADDRESS))
                .findFirst()
                .or(() -> Optional.of(results[0]));
    }

    private PlaceDTO saveOrUpdatePlace(GeocodingResult geocodingResult, LatLng coords) {
        String placeId = geocodingResult.placeId;
        Place place = placeRepository.findByGooglePlaceId(placeId).orElseGet(Place::new);
        place.setGooglePlaceId(placeId);
        place.setName(cleanAddressName(geocodingResult.formattedAddress));
        place.setAddress(extractCityFromGeocoding(geocodingResult.addressComponents));
        place.setLocation(geometryFactory.createPoint(new Coordinate(coords.lng, coords.lat)));
        Place savedPlace = placeRepository.save(place);
        return new PlaceDTO(savedPlace.getId(), savedPlace.getGooglePlaceId(), savedPlace.getName(), savedPlace.getAddress(), null);
    }

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

    private String extractCityFromGeocoding(AddressComponent[] components) {
        return Arrays.stream(components)
                .filter(c -> Arrays.asList(c.types).contains(AddressComponentType.LOCALITY))
                .map(c -> c.longName)
                .findFirst()
                .orElse(null);
    }

    private String cleanAddressName(String fullAddress) {
        if (fullAddress == null) return null;
        return fullAddress.split(",")[0];
    }

    // Neue Helper-Methode, um den reinen Strassennamen ohne Nummer zu extrahieren
    private String getStreetNameFromAddress(String address) {
        if (address == null || address.isEmpty()) return "";
        // Entfernt Zahlen und alles was danach kommt
        return address.replaceAll("\\d+.*", "").trim();
    }
}