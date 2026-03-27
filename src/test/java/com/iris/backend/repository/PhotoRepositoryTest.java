package com.iris.backend.repository;

import com.iris.backend.model.*;
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

class PhotoRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PhotoRepository photoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GooglePlaceRepository googlePlaceRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private User currentUser;
    private User otherUser;
    private GooglePlace testPlace;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        googlePlaceRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Feste Zeit in UTC
        baseTime = OffsetDateTime.parse("2026-01-01T12:00:00Z");

        // 2. User erstellen
        currentUser = new User();
        currentUser.setFirebaseUid("uid-current");
        currentUser.setUsername("CurrentUser");
        currentUser.setEmail("current@test.com");
        userRepository.saveAndFlush(currentUser);

        otherUser = new User();
        otherUser.setFirebaseUid("uid-other");
        otherUser.setUsername("OtherUser");
        otherUser.setEmail("other@test.com");
        userRepository.saveAndFlush(otherUser);

        // 3. Ort erstellen (Wir nehmen hier nur einen Google Place als Test-Szenario)
        testPlace = new GooglePlace();
        testPlace.setGooglePlaceId("g-test-place");
        testPlace.setName("Test Location");
        testPlace.setLocation(createPoint(7.4474, 46.9480));
        testPlace.setRadiusMeters(500);
        testPlace.setImportance(5);
        googlePlaceRepository.saveAndFlush(testPlace);
    }

    @Test
    void testFindPhotosForGooglePlaceMatchingHistoricalBatchFromOthers() {
        // --- ARRANGE ---
        // Foto 1: Von einem ANDEREN User, PUBLIC, 1 Stunde alt -> SOLL GEFUNDEN WERDEN
        createPhoto(otherUser, testPlace, baseTime.minusHours(1), PhotoVisibility.PUBLIC);

        // Foto 2: Von einem ANDEREN User, FRIENDS, 1 Stunde alt -> IGNORIEREN (Nur Public/VisibleToAll)
        createPhoto(otherUser, testPlace, baseTime.minusHours(1), PhotoVisibility.FRIENDS);

        // Foto 3: Von MIR SELBST, PUBLIC, 1 Stunde alt -> IGNORIEREN (Ich will nur Fotos von anderen)
        createPhoto(currentUser, testPlace, baseTime.minusHours(1), PhotoVisibility.PUBLIC);

        // Foto 4: Von einem ANDEREN User, PUBLIC, 6 Stunden alt -> IGNORIEREN (außerhalb 5h Fenster)
        createPhoto(otherUser, testPlace, baseTime.minusHours(6), PhotoVisibility.PUBLIC);

        String timestampStr = baseTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String historyJson = String.format(
                "[{\"latitude\": 46.9480, \"longitude\": 7.4474, \"timestamp\": \"%s\"}]",
                timestampStr
        );

        // --- EXECUTE ---
        List<Photo> foundPhotos = photoRepository.findPhotosForGooglePlaceMatchingHistoricalBatchFromOthers(
                testPlace.getId(), historyJson, currentUser.getId()
        );

        // --- ASSERT ---
        // Es darf exakt nur 1 Foto übrig bleiben (Foto 1)
        assertThat(foundPhotos).hasSize(1);
        assertThat(foundPhotos.get(0).getUploader().getId()).isEqualTo(otherUser.getId());
        assertThat(foundPhotos.get(0).getVisibility()).isEqualTo(PhotoVisibility.PUBLIC);
    }

    @Test
    void testFindFriendsFeedPhotos() {
        // --- ARRANGE ---
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Foto 1: FRIENDS (gültig) -> FINDEN
        createPhoto(otherUser, testPlace, now.minusHours(1), PhotoVisibility.FRIENDS);

        // Foto 2: VISIBLE_TO_ALL (Hybrid, gültig) -> FINDEN
        createPhoto(otherUser, testPlace, now.minusHours(1), PhotoVisibility.VISIBLE_TO_ALL);

        // Foto 3: PUBLIC (nur für Map/Spot) -> IGNORIEREN
        createPhoto(otherUser, testPlace, now.minusHours(1), PhotoVisibility.PUBLIC);

        // Foto 4: FRIENDS, aber in der Vergangenheit abgelaufen! -> IGNORIEREN
        Photo expiredPhoto = new Photo();
        expiredPhoto.setUploader(otherUser);
        expiredPhoto.setUploadedAt(now.minusDays(2));
        expiredPhoto.setExpiresAt(now.minusDays(1)); // Gestern abgelaufen!
        expiredPhoto.setVisibility(PhotoVisibility.FRIENDS);
        expiredPhoto.setStorageUrl("expired.jpg");
        expiredPhoto.setGooglePlace(testPlace);
        expiredPhoto.setLocation(testPlace.getLocation());
        photoRepository.saveAndFlush(expiredPhoto);

        // --- EXECUTE ---
        // Wir übergeben "otherUser" als Liste der Freunde des aktuellen Users
        List<Photo> feedPhotos = photoRepository.findFriendsFeedPhotos(List.of(otherUser), now);

        // --- ASSERT ---
        // Sollte exakt Foto 1 und Foto 2 finden.
        assertThat(feedPhotos).hasSize(2);
        assertThat(feedPhotos).extracting(Photo::getVisibility)
                .containsExactlyInAnyOrder(PhotoVisibility.FRIENDS, PhotoVisibility.VISIBLE_TO_ALL);
    }

    // --- HILFSMETHODEN ---
    private Point createPoint(double lon, double lat) {
        Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private void createPhoto(User uploader, GooglePlace gp, OffsetDateTime uploadedAt, PhotoVisibility visibility) {
        Photo p = new Photo();
        p.setUploader(uploader);
        p.setUploadedAt(uploadedAt);
        p.setExpiresAt(uploadedAt.plusDays(1));
        p.setVisibility(visibility);
        p.setStorageUrl("test-" + visibility.name() + ".jpg");
        p.setGooglePlace(gp);
        p.setLocation(gp.getLocation());

        Photo saved = photoRepository.saveAndFlush(p);

        // Zwinge das "uploaded_at" Datum hart in die Datenbank
        jdbcTemplate.update("UPDATE photos SET uploaded_at = ? WHERE id = ?",
                java.sql.Timestamp.from(uploadedAt.toInstant()), saved.getId());
    }
}