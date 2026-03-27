package com.iris.backend.repository;

import com.iris.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private User currentUser;
    private User nearbyFriend;
    private User distantFriend;
    private User nearbyStranger;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // 1. Mich selbst (Zentrum in Bern)
        currentUser = createUser("uid-1", "Noah", "noah@test.com", 7.4474, 46.9480);

        // 2. Ein Freund, der sehr nah ist (ca. 1km entfernt)
        nearbyFriend = createUser("uid-2", "Anna_Friend", "anna@test.com", 7.4580, 46.9500);

        // 3. Ein Freund, der weit weg ist (Zürich)
        distantFriend = createUser("uid-3", "Max_Friend", "max@test.com", 8.5417, 47.3769);

        // 4. Ein Fremder, der aber sehr nah ist
        nearbyStranger = createUser("uid-4", "Stranger_Anna", "stranger@test.com", 7.4480, 46.9490);
    }

    @Test
    void testFindFriendsByIdsAndLocation_ShouldReturnOnlyNearbyFriends() {
        // Wir suchen von Bern aus im Umkreis von 5km nach unseren beiden Freunden
        Point searchLocation = createPoint(7.4474, 46.9480);
        List<UUID> friendIds = List.of(nearbyFriend.getId(), distantFriend.getId());

        List<User> foundFriends = userRepository.findFriendsByIdsAndLocation(friendIds, searchLocation, 5000);

        // Sollte NUR den nahen Freund finden (Fremder ist kein Freund, Zürich ist zu weit)
        assertThat(foundFriends).hasSize(1);
        assertThat(foundFriends.get(0).getUsername()).isEqualTo("Anna_Friend");
    }

    @Test
    void testFindNearbyUsersByLocation_ShouldExcludeSelf() {
        // Wir suchen von Bern aus alle Leute im Umkreis von 5km
        List<User> nearbyUsers = userRepository.findNearbyUsersByLocation(46.9480, 7.4474, 5000, currentUser.getId());

        // Sollte den nahen Freund und den nahen Fremden finden, aber NICHT MICH SELBST!
        assertThat(nearbyUsers).hasSize(2);
        assertThat(nearbyUsers).extracting(User::getUsername)
                .containsExactlyInAnyOrder("Anna_Friend", "Stranger_Anna");
    }

    @Test
    void testSearchUsers_ShouldReturnCaseInsensitiveMatchesAndExcludeSelf() {
        // Suche nach "anna" (sollte Anna_Friend und Stranger_Anna finden)
        Page<User> results = userRepository.searchUsers("anna", currentUser.getId(), PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent()).extracting(User::getUsername)
                .containsExactlyInAnyOrder("Anna_Friend", "Stranger_Anna");
    }

    private Point createPoint(double lon, double lat) {
        Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private User createUser(String uid, String username, String email, double lon, double lat) {
        User u = new User();
        u.setFirebaseUid(uid);
        u.setUsername(username);
        u.setEmail(email);
        u.setLastLocation(createPoint(lon, lat));
        u.setLastLocationUpdatedAt(OffsetDateTime.now());
        u.setCreatedAt(OffsetDateTime.now());
        return userRepository.saveAndFlush(u);
    }
}