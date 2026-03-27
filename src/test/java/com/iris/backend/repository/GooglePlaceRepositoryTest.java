package com.iris.backend.repository;

import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GooglePlaceRepositoryTest extends AbstractRepositoryTest {

    @Autowired private GooglePlaceRepository googlePlaceRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private User testUser;
    private GooglePlace activePlace;
    private GooglePlace inactivePlace;
    private GooglePlace farPlace;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        googlePlaceRepository.deleteAll();
        userRepository.deleteAll();

        baseTime = OffsetDateTime.now(ZoneOffset.UTC);

        testUser = new User();
        testUser.setFirebaseUid("uid-google-test");
        testUser.setUsername("GoogleTester");
        testUser.setEmail("gt@test.com");
        userRepository.saveAndFlush(testUser);

        // Ort 1: Bern (Hier posten wir gleich ein gültiges Foto)
        activePlace = createGooglePlace("g-1", "Aktiver Ort Bern", 7.4474, 46.9480);

        // Ort 2: Bern (Hier posten wir nur ein ZU ALTES Foto)
        inactivePlace = createGooglePlace("g-2", "Inaktiver Ort Bern", 7.4480, 46.9490);

        // Ort 3: Zürich (Hier posten wir ein gültiges Foto, ist aber zu weit weg)
        farPlace = createGooglePlace("g-3", "Zürich Ort", 8.5417, 47.3769);

        // --- FOTOS ERSTELLEN ---
        // Gültiges Foto für Ort 1 (vor 1 Stunde)
        createPhoto(activePlace, baseTime.minusHours(1), PhotoVisibility.PUBLIC);

        // Zu altes Foto für Ort 2 (vor 6 Stunden -> außerhalb 5h Fenster)
        createPhoto(inactivePlace, baseTime.minusHours(6), PhotoVisibility.PUBLIC);

        // Gültiges Foto für Ort 3 (vor 1 Stunde, aber Zürich)
        createPhoto(farPlace, baseTime.minusHours(1), PhotoVisibility.PUBLIC);
    }

    @Test
    void testFindPlacesWithActivePublicPhotosInTimeWindow() {
        // Wir suchen von Bern aus im 5km Radius
        List<GooglePlace> places = googlePlaceRepository.findPlacesWithActivePublicPhotosInTimeWindow(
                46.9480, 7.4474, 5000, baseTime
        );

        // Erwartung:
        // - activePlace gefunden (nah genug + aktuelles Foto)
        // - inactivePlace ignoriert (Foto ist > 5h alt)
        // - farPlace ignoriert (Zürich ist weiter als 5km entfernt)
        assertThat(places).hasSize(1);
        assertThat(places.get(0).getName()).isEqualTo("Aktiver Ort Bern");
    }

    @Test
    void testFindPlacesMatchingHistoricalBatch() {
        String timestampStr = baseTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String historyJson = String.format(
                "[{\"latitude\": 46.9480, \"longitude\": 7.4474, \"timestamp\": \"%s\"}]",
                timestampStr
        );

        List<GooglePlace> places = googlePlaceRepository.findPlacesMatchingHistoricalBatch(historyJson, 5000);

        // Gleiche Erwartung wie oben, aber durch JSON Batch geparst
        assertThat(places).hasSize(1);
        assertThat(places.get(0).getName()).isEqualTo("Aktiver Ort Bern");
    }

    private Point createPoint(double lon, double lat) {
        Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private GooglePlace createGooglePlace(String googleId, String name, double lon, double lat) {
        GooglePlace gp = new GooglePlace();
        gp.setGooglePlaceId(googleId);
        gp.setName(name);
        gp.setLocation(createPoint(lon, lat));
        gp.setRadiusMeters(500);
        gp.setImportance(5);
        gp.setCreatedAt(OffsetDateTime.now());
        return googlePlaceRepository.saveAndFlush(gp);
    }

    private void createPhoto(GooglePlace gp, OffsetDateTime uploadedAt, PhotoVisibility visibility) {
        Photo p = new Photo();
        p.setUploader(testUser);
        p.setUploadedAt(uploadedAt);
        p.setExpiresAt(uploadedAt.plusDays(1));
        p.setVisibility(visibility);
        p.setStorageUrl("url.jpg");
        p.setGooglePlace(gp);
        p.setLocation(gp.getLocation());

        Photo saved = photoRepository.saveAndFlush(p);

        jdbcTemplate.update("UPDATE photos SET uploaded_at = ? WHERE id = ?",
                java.sql.Timestamp.from(uploadedAt.toInstant()), saved.getId());
    }
}