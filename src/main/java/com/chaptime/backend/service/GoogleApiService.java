package com.chaptime.backend.service;

import com.chaptime.backend.model.Place;
import com.chaptime.backend.repository.PlaceRepository;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public Optional<Place> findOrCreatePlaceForCoordinates(double latitude, double longitude) {
        try {
            LatLng coords = new LatLng(latitude, longitude);

            // Schritt 1: IMMER die bestmögliche Adresse via Reverse Geocoding holen
            GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoApiContext, coords).await();
            if (geocodingResults.length == 0) {
                logger.warn("Reverse Geocoding returned no results for coords: {}", coords);
                return Optional.empty(); // Keine Adresse, kein Ort
            }
            // Wir merken uns die beste Adresse und die PlaceID von der Geocoding-Antwort
            String preciseAddress = geocodingResults[0].formattedAddress;
            String geocodingPlaceId = geocodingResults[0].placeId;


            // Schritt 2: SUCHE nach einem Point of Interest (POI) in der Nähe
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoApiContext, coords).radius(50).await();

            // Fall A: POI gefunden (z.B. "Bahnhof Münchenbuchsee")
            if (placesResponse.results.length > 0) {
                PlacesSearchResult topPlace = placesResponse.results[0];
                // Wir nehmen die ID vom POI, weil sie spezifischer ist
                return Optional.of(placeRepository.findByGooglePlaceId(topPlace.placeId).orElseGet(() -> {
                    Place newPlace = new Place();
                    newPlace.setGooglePlaceId(topPlace.placeId);
                    newPlace.setName(topPlace.name);        // Der Name des Ortes
                    newPlace.setAddress(preciseAddress);    // Die genaue Adresse
                    newPlace.setLocation(geometryFactory.createPoint(new Coordinate(topPlace.geometry.location.lng, topPlace.geometry.location.lat)));
                    return placeRepository.save(newPlace);
                }));
            }
            // Fall B: Kein POI gefunden, wir verwenden die Daten von der Adress-Suche
            else {
                return Optional.of(placeRepository.findByGooglePlaceId(geocodingPlaceId).orElseGet(() -> {
                    Place newPlace = new Place();
                    newPlace.setGooglePlaceId(geocodingPlaceId);
                    newPlace.setName(preciseAddress);       // Adresse als Name
                    newPlace.setAddress(preciseAddress);    // Adresse als Adresse
                    newPlace.setLocation(geometryFactory.createPoint(new Coordinate(longitude, latitude)));
                    return placeRepository.save(newPlace);
                }));
            }

        } catch (Exception e) {
            logger.error("Error calling Google APIs: {}", e.getMessage());
        }

        return Optional.empty();
    }
}