package com.iris.backend.repository;

import com.iris.backend.model.Friendship;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Verhindert, dass Spring nach H2 sucht
@Testcontainers
@ActiveProfiles("test")
class FriendshipRepositoryTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:15-3.3")
            .asCompatibleSubstituteFor("postgres");


    @Container
    static PostgreSQLContainer<?> postgisContainer = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("iris_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgisContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgisContainer::getUsername);
        registry.add("spring.datasource.password", postgisContainer::getPassword);
    }

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    private User currentUser;
    private User bestFriend;
    private User oldFriend;
    private User newFriend;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        // 2. Erstelle Test-User mit unserer neuen Hilfsmethode
        currentUser = createUser("uid-1", "Current", "current@test.com");
        bestFriend = createUser("uid-2", "Bestie", "bestie@test.com");
        oldFriend = createUser("uid-3", "Oldie", "oldie@test.com");
        newFriend = createUser("uid-4", "Newbie", "newbie@test.com");

        // 3. Erstelle Freundschaften mit verschiedenen Scores
        // "Oldie": Score 10, aber Interaktion war vor 5 Tagen
        createFriendship(currentUser, oldFriend, 10, OffsetDateTime.now().minusDays(5));

        // "Bestie": Score 10, Interaktion war HEUTE (Sollte Platz 1 sein, da gleicher Score aber aktueller!)
        createFriendship(currentUser, bestFriend, 10, OffsetDateTime.now());

        // "Newbie": Score 2, Interaktion war heute (Sollte Platz 3 sein, da Score niedriger)
        createFriendship(currentUser, newFriend, 2, OffsetDateTime.now());
    }

    private User createUser(String uid, String username, String email) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setUsername(username);
        user.setEmail(email);
        return userRepository.save(user);
    }

    @Test
    void testFindFriendsSortedByInteraction_ShouldReturnPerfectOrder() {
        // --- EXECUTE ---
        List<Friendship> sortedFriends = friendshipRepository.findFriendsSortedByInteraction(currentUser);

        // --- ASSERT ---
        assertThat(sortedFriends).hasSize(3);

        // Platz 1 muss "Bestie" sein (Hoher Score + Aktuell)
        assertThat(getFriend(sortedFriends.get(0)).getUsername()).isEqualTo("Bestie");

        // Platz 2 muss "Oldie" sein (Hoher Score, aber älter)
        assertThat(getFriend(sortedFriends.get(1)).getUsername()).isEqualTo("Oldie");

        // Platz 3 muss "Newbie" sein (Niedriger Score)
        assertThat(getFriend(sortedFriends.get(2)).getUsername()).isEqualTo("Newbie");
    }

    // Hilfsmethode zum schnellen Erstellen
    private void createFriendship(User u1, User u2, int score, OffsetDateTime lastInteracted) {
        Friendship f = new Friendship();
        f.setUserOne(u1);
        f.setUserTwo(u2);
        f.setActionUser(u1);
        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setInteractionScore(score);
        f.setLastInteractedAt(lastInteracted);
        friendshipRepository.save(f);
    }

    // Hilfsmethode, um den "anderen" User aus der Freundschaft zu holen
    private User getFriend(Friendship friendship) {
        return friendship.getUserOne().getId().equals(currentUser.getId()) ? friendship.getUserTwo() : friendship.getUserOne();
    }
}