package com.iris.backend.repository;

import com.iris.backend.model.*;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.model.enums.PlaceAccessType;
import jakarta.persistence.EntityManager;
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

class HistoricalFeedRepositoryTest extends AbstractRepositoryTest {

    @Autowired private HistoricalFeedRepository historicalFeedRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GooglePlaceRepository googlePlaceRepository;
    @Autowired private CustomPlaceRepository customPlaceRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private User testUser;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        googlePlaceRepository.deleteAll();
        customPlaceRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Wir nutzen die ECHTE aktuelle Zeit, damit NOW() im SQL funktioniert!
        baseTime = OffsetDateTime.now(ZoneOffset.UTC);

        // 2. User erstellen
        testUser = new User();
        testUser.setFirebaseUid("test-uid-1");
        testUser.setUsername("FeedTester");
        testUser.setEmail("tester@feed.com");
        userRepository.saveAndFlush(testUser);

        // --- ORTE ERSTELLEN ---
        GooglePlace googlePlace = new GooglePlace();
        googlePlace.setGooglePlaceId("g-bern-1");
        googlePlace.setName("Bern Bärenpark");
        googlePlace.setLocation(createPoint(7.4474, 46.9480));
        googlePlace.setRadiusMeters(500);
        googlePlace.setImportance(5);
        googlePlaceRepository.saveAndFlush(googlePlace);

        CustomPlace customPlace = new CustomPlace();
        customPlace.setCreator(testUser);
        customPlace.setName("Bern Geheimtipp");
        customPlace.setLocation(createPoint(7.4480, 46.9490));
        customPlace.setRadiusMeters(500);
        customPlace.setAccessType(PlaceAccessType.PUBLIC);
        customPlace.setLive(true);
        customPlace.setTrending(false);
        // Läuft erst in 24 Stunden ab, NOW() ist also safe!
        customPlace.setExpiresAt(baseTime.plusDays(1));
        customPlaceRepository.saveAndFlush(customPlace);

        GooglePlace farPlace = new GooglePlace();
        farPlace.setGooglePlaceId("g-zrh-1");
        farPlace.setName("Zürich HB");
        farPlace.setLocation(createPoint(8.5417, 47.3769));
        farPlace.setRadiusMeters(500);
        farPlace.setImportance(5);
        googlePlaceRepository.saveAndFlush(farPlace);

        // --- FOTOS ERSTELLEN ---
        createPhoto(googlePlace, null, baseTime.minusHours(1), PhotoVisibility.PUBLIC);
        createPhoto(null, customPlace, baseTime.minusHours(2), PhotoVisibility.VISIBLE_TO_ALL);
        createPhoto(null, customPlace, baseTime.minusHours(1), PhotoVisibility.FRIENDS); // Ignorieren (Friends)
        createPhoto(googlePlace, null, baseTime.minusHours(6), PhotoVisibility.PUBLIC); // Ignorieren (Zu alt)
        createPhoto(farPlace, null, baseTime.minusHours(1), PhotoVisibility.PUBLIC); // Ignorieren (Zu weit weg)

        // Lösche den Cache von Hibernate, damit die native Query frisch aus der DB liest
        entityManager.clear();
    }

    @Test
    void testFindHistoricalFeed_ShouldReturnCorrectPlacesWithCorrectCounts() {
        // --- ARRANGE ---
        String timestampStr = baseTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String historyJson = String.format(
                "[{\"latitude\": 46.9480, \"longitude\": 7.4474, \"timestamp\": \"%s\"}]",
                timestampStr
        );

        // --- EXECUTE ---
        var feedItems = historicalFeedRepository.findHistoricalFeed(historyJson);

        // --- ASSERT ---
        assertThat(feedItems).hasSize(2);

        List<String> foundNames = feedItems.stream().map(item -> item.getName()).toList();
        assertThat(foundNames).containsExactlyInAnyOrder("Bern Bärenpark", "Bern Geheimtipp");

        var googleItem = feedItems.stream().filter(i -> i.getName().equals("Bern Bärenpark")).findFirst().get();
        assertThat(googleItem.getPhotoCount()).isEqualTo(1L);

        var customItem = feedItems.stream().filter(i -> i.getName().equals("Bern Geheimtipp")).findFirst().get();
        assertThat(customItem.getPhotoCount()).isEqualTo(1L);
    }

    private Point createPoint(double lon, double lat) {
        Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private void createPhoto(GooglePlace gp, CustomPlace cp, OffsetDateTime uploadedAt, PhotoVisibility visibility) {
        Photo p = new Photo();
        p.setUploader(testUser);
        p.setUploadedAt(uploadedAt);
        p.setExpiresAt(uploadedAt.plusDays(1));
        p.setVisibility(visibility);
        p.setStorageUrl("test-url.jpg");

        if (gp != null) {
            p.setGooglePlace(gp);
            p.setLocation(gp.getLocation());
        } else if (cp != null) {
            p.setCustomPlace(cp);
            p.setLocation(cp.getLocation());
        }

        // saveAndFlush zwingt Hibernate, sofort in die Datenbank zu schreiben!
        Photo saved = photoRepository.saveAndFlush(p);

        // Jetzt überschreiben wir das von Hibernate (evtl. durch @CreationTimestamp) erzeugte Datum sicher:
        jdbcTemplate.update("UPDATE photos SET uploaded_at = ? WHERE id = ?",
                java.sql.Timestamp.from(uploadedAt.toInstant()), saved.getId());
    }
}