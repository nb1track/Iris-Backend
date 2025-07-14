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
import java.util.stream.Stream;
import java.util.Map;
import java.util.LinkedHashMap;


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
     * Finds nearby places by first using Reverse Geocoding for a precise address,
     * then supplements with Points of Interest from the Places API.
     */
    public List<PlaceDTO> findNearbyPlaces(double latitude, double longitude) {
        Map<String, PlaceDTO> placesMap = new LinkedHashMap<>(); // LinkedHashMap behält die Einfügereihenfolge bei
        LatLng coords = new LatLng(latitude, longitude);

        // --- STUFE 1: Reverse Geocoding API für die exakteste Adresse ---
        try {
            GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, coords).await();

            // Finde das beste Ergebnis (idealerweise eine Strassenadresse)
            Optional<GeocodingResult> bestAddress = findBestGeocodingResult(geocodingResults);

            if (bestAddress.isPresent()) {
                PlaceDTO precisePlace = saveOrUpdatePlace(bestAddress.get(), coords);
                placesMap.put(precisePlace.googlePlaceId(), precisePlace);
                logger.info("Found precise address via Geocoding: {}", precisePlace.name());
            }

        } catch (Exception e) {
            logger.error("Error calling Google Geocoding API: {}", e.getMessage());
        }

        // --- STUFE 2: Places API für interessante Orte (POIs) ---
        try {
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoApiContext, coords)
                    .radius(25) // Radius für POIs
                    .await();

            Arrays.stream(placesResponse.results)
                    .map(this::saveOrUpdatePlace)
                    // Füge nur hinzu, wenn noch nicht durch Geocoding gefunden
                    .forEach(placeDto -> placesMap.putIfAbsent(placeDto.googlePlaceId(), placeDto));

        } catch (Exception e) {
            logger.error("Error calling Google Places API: {}", e.getMessage());
        }

        return new ArrayList<>(placesMap.values());
    }

    /**
     * Wählt das beste Geocoding-Ergebnis aus, bevorzugt Strassenadressen.
     */
    private Optional<GeocodingResult> findBestGeocodingResult(GeocodingResult[] results) {
        if (results == null || results.length == 0) {
            return Optional.empty();
        }
        // Bevorzuge immer eine exakte Strassenadresse
        return Arrays.stream(results)
                .filter(r -> Arrays.asList(r.types).contains(AddressType.STREET_ADDRESS))
                .findFirst()
                // Wenn keine Strassenadresse gefunden wurde, nimm das erste Ergebnis als Fallback
                .or(() -> Optional.of(results[0]));
    }

    /**
     * Helper-Methode für GeocodingResult.
     */
    private PlaceDTO saveOrUpdatePlace(GeocodingResult geocodingResult, LatLng coords) {
        String placeId = geocodingResult.placeId;
        Place place = placeRepository.findByGooglePlaceId(placeId).orElseGet(Place::new);

        place.setGooglePlaceId(placeId);
        // Wir nehmen die formatierte Adresse und versuchen, sie zu bereinigen
        place.setName(cleanAddressName(geocodingResult.formattedAddress));
        place.setAddress(extractCityFromGeocoding(geocodingResult.addressComponents));
        place.setLocation(geometryFactory.createPoint(new Coordinate(coords.lng, coords.lat)));

        Place savedPlace = placeRepository.save(place);
        return new PlaceDTO(savedPlace.getId(), savedPlace.getGooglePlaceId(), savedPlace.getName(), savedPlace.getAddress(), null);
    }

    /**
     * Helper-Methode für PlacesSearchResult (POI).
     */
    private PlaceDTO saveOrUpdatePlace(PlacesSearchResult placeResult) {
        Place place = placeRepository.findByGooglePlaceId(placeResult.placeId).orElseGet(Place::new);
        place.setGooglePlaceId(placeResult.placeId);
        place.setName(placeResult.name);

        // Ignoriere die Adresse, wenn es ein Plus Code ist
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
        for (AddressComponent component : components) {
            for (AddressComponentType type : component.types) {
                if (type == AddressComponentType.LOCALITY) {
                    return component.longName;
                }
            }
        }
        return null;
    }

    // Bereinigt den Adress-String, um nur die Strasse und Hausnummer zu behalten
    private String cleanAddressName(String fullAddress) {
        if (fullAddress == null) return null;
        // Beispiel: "Brunnenweg 14, 3053 Münchenbuchsee, Schweiz" -> "Brunnenweg 14"
        return fullAddress.split(",")[0];
    }
}