package com.iris.backend.repository;

import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PlaceAccessType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomPlaceRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private CustomPlaceRepository customPlaceRepository;

    @Autowired
    private UserRepository userRepository;

    private User creator;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @BeforeEach
    void setUp() {
        customPlaceRepository.deleteAll();
        userRepository.deleteAll();

        // Erstelle einen Test-User
        creator = new User();
        creator.setFirebaseUid("creator-uid");
        creator.setUsername("SpotCreator");
        creator.setEmail("creator@test.com");
        userRepository.save(creator);

        // Erstelle Spots an verschiedenen Orten
        // Spot 1: Direkt im Zentrum (Bern: Lat 46.9480, Lon 7.4474) -> LIVE
        createPlace("Zentrum Spot", 7.4474, 46.9480, true, true);

        // Spot 2: Etwas weiter weg, aber noch nah (ca. 1km entfernt) -> LIVE
        createPlace("Naher Spot", 7.4580, 46.9500, false, true);

        // Spot 3: Weit weg (Zürich: Lat 47.3769, Lon 8.5417) -> LIVE
        createPlace("Weiter Spot", 8.5417, 47.3769, true, true);

        // Spot 4: Im Zentrum, aber NICHT live! (Sollte ignoriert werden)
        createPlace("Inaktiver Spot", 7.4474, 46.9480, false, false);
    }

    @Test
    void testFindActivePlacesForUserLocation_ShouldReturnOnlyLivePlacesWithinRadius() {
        // --- EXECUTE ---
        // Wir suchen von Bern aus (Lat: 46.9480, Lon: 7.4474)
        List<CustomPlace> foundPlaces = customPlaceRepository.findActivePlacesForUserLocation(46.9480, 7.4474);

        // --- ASSERT ---
        // Es sollten nur Spot 1 (Zentrum) und Spot 2 (Nah) gefunden werden.
        // Spot 3 (Zürich) ist zu weit weg. Spot 4 ist nicht live!
        assertThat(foundPlaces).hasSize(2);
        assertThat(foundPlaces).extracting(CustomPlace::getName)
                .containsExactlyInAnyOrder("Zentrum Spot", "Naher Spot");
    }

    @Test
    void testFindAllByIsTrendingTrueOrderByCreatedAtDesc_ShouldReturnOnlyTrending() {
        // --- EXECUTE ---
        List<CustomPlace> trendingPlaces = customPlaceRepository.findAllByIsTrendingTrueOrderByCreatedAtDesc();

        // --- ASSERT ---
        // Nur Zentrum Spot und Weiter Spot wurden als "trending" markiert
        assertThat(trendingPlaces).hasSize(2);
        assertThat(trendingPlaces).extracting(CustomPlace::getName)
                .containsExactlyInAnyOrder("Zentrum Spot", "Weiter Spot");
    }

    // Hilfsmethode zum Erstellen eines Geodaten-Punktes (Lon, Lat)
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326); // WGS84 Standard für GPS
        return point;
    }

    // Hilfsmethode zum Speichern von Orten
    private void createPlace(String name, double lon, double lat, boolean isTrending, boolean isLive) {
        CustomPlace place = new CustomPlace();
        place.setCreator(creator);
        place.setName(name);
        place.setLocation(createPoint(lon, lat));
        place.setRadiusMeters(5000); // 5km Radius für diese Tests
        place.setAccessType(PlaceAccessType.PUBLIC);
        place.setTrending(isTrending);
        place.setLive(isLive);
        place.setChallengesActivated(false);
        place.setCreatedAt(OffsetDateTime.now());
        place.setExpiresAt(OffsetDateTime.now().plusDays(1)); // Läuft erst morgen ab
        customPlaceRepository.save(place);
    }
}