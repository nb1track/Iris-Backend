package com.iris.backend.service;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.ParticipantDTO;
import com.iris.backend.dto.UpdateCustomPlaceRequestDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.model.enums.PlaceAccessType;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomPlaceServiceTest {

    @Mock private CustomPlaceRepository customPlaceRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private GcsStorageService gcsStorageService;

    @InjectMocks
    private CustomPlaceService customPlaceService;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final String PROFILES_BUCKET = "test-profiles-bucket";

    private User creator;
    private User participantFriend;
    private User participantStranger;
    private CustomPlace customPlace;
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(customPlaceService, "profileImagesBucketName", PROFILES_BUCKET);

        creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setUsername("Creator");
        // Setze die Location des Creators (z.B. Bern)
        creator.setLastLocation(createPoint(7.4474, 46.9480));

        participantFriend = new User();
        participantFriend.setId(UUID.randomUUID());
        participantFriend.setUsername("Friend");
        participantFriend.setProfileImageUrl("friend.jpg");

        participantStranger = new User();
        participantStranger.setId(UUID.randomUUID());
        participantStranger.setUsername("Stranger");

        customPlace = new CustomPlace();
        customPlace.setId(placeId);
        customPlace.setCreator(creator);
        customPlace.setName("My Secret Spot");
        customPlace.setRadiusMeters(100);
    }

    @Test
    void createCustomPlace_ShouldSavePlace_WhenUserIsNearLocation() throws Exception {
        // --- ARRANGE ---
        MultipartFile mockImage = mock(MultipartFile.class);
        // Wir versuchen, den Spot exakt dort zu erstellen, wo der Creator steht
        CreateCustomPlaceRequestDTO request = new CreateCustomPlaceRequestDTO(
                "New Spot", 46.9480, 7.4474, 100, PlaceAccessType.PUBLIC, null,
                true, true, null, OffsetDateTime.now().plusDays(1), false
        );

        when(gcsStorageService.uploadPhoto(mockImage)).thenReturn("cover.jpg");
        when(customPlaceRepository.save(any(CustomPlace.class))).thenAnswer(i -> i.getArgument(0));

        // --- EXECUTE ---
        CustomPlace savedPlace = customPlaceService.createCustomPlace(request, mockImage, creator);

        // --- ASSERT ---
        assertThat(savedPlace.getName()).isEqualTo("New Spot");
        assertThat(savedPlace.getCoverImageUrl()).isEqualTo("cover.jpg");
        assertThat(savedPlace.isLive()).isTrue();
        verify(customPlaceRepository).save(any(CustomPlace.class));
    }

    @Test
    void createCustomPlace_ShouldThrowException_WhenUserIsTooFar() {
        // --- ARRANGE ---
        MultipartFile mockImage = mock(MultipartFile.class);

        // Da .distance() in Grad statt Metern misst, setzen wir die Location
        // des Creators hier einfach auf NULL, um die Exception absolut sicher auszulösen!
        creator.setLastLocation(null);

        CreateCustomPlaceRequestDTO request = new CreateCustomPlaceRequestDTO(
                "Far Spot", 47.3769, 8.5417, 100, PlaceAccessType.PUBLIC, null,
                true, true, null, OffsetDateTime.now().plusDays(1), false
        );

        // --- EXECUTE & ASSERT ---
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            customPlaceService.createCustomPlace(request, mockImage, creator);
        });

        assertThat(exception.getMessage()).isEqualTo("User must be near the location to create a custom place.");
        verifyNoInteractions(gcsStorageService, customPlaceRepository);
    }

    @Test
    void getParticipants_ShouldReturnParticipantsWithFriendStatus_WhenUserIsCreator() {
        // --- ARRANGE ---
        when(customPlaceRepository.findById(placeId)).thenReturn(Optional.of(customPlace));

        // Fake die Teilnehmer des Spots
        when(photoRepository.findDistinctUploadersByCustomPlace(customPlace))
                .thenReturn(List.of(participantFriend, participantStranger));

        // Fake die Freundschaft (Creator ist mit 'participantFriend' befreundet)
        Friendship friendship = new Friendship();
        friendship.setUserOne(creator);
        friendship.setUserTwo(participantFriend);

        when(friendshipRepository.findByUserOneAndStatusOrUserTwoAndStatus(
                creator, FriendshipStatus.ACCEPTED, creator, FriendshipStatus.ACCEPTED
        )).thenReturn(List.of(friendship));

        // Fake die URL Generierung für den Freund
        when(gcsStorageService.generateSignedUrl(PROFILES_BUCKET, "friend.jpg", 15, TimeUnit.MINUTES))
                .thenReturn("https://signed.com/friend.jpg");

        // --- EXECUTE ---
        List<ParticipantDTO> participants = customPlaceService.getParticipants(placeId, creator);

        // --- ASSERT ---
        assertThat(participants).hasSize(2);

        // Prüfe den Freund
        ParticipantDTO friendDTO = participants.stream().filter(p -> p.username().equals("Friend")).findFirst().get();
        assertThat(friendDTO.isFriend()).isTrue();
        assertThat(friendDTO.profileImageUrl()).isEqualTo("https://signed.com/friend.jpg");

        // Prüfe den Fremden
        ParticipantDTO strangerDTO = participants.stream().filter(p -> p.username().equals("Stranger")).findFirst().get();
        assertThat(strangerDTO.isFriend()).isFalse();
        assertThat(strangerDTO.profileImageUrl()).isNull();
    }

    @Test
    void getParticipants_ShouldThrowSecurityException_WhenUserIsNotCreator() {
        // --- ARRANGE ---
        when(customPlaceRepository.findById(placeId)).thenReturn(Optional.of(customPlace));

        // Ein Fremder versucht, die Teilnehmerliste abzurufen
        User maliciousUser = new User();
        maliciousUser.setId(UUID.randomUUID());

        // --- EXECUTE & ASSERT ---
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            customPlaceService.getParticipants(placeId, maliciousUser);
        });

        assertThat(exception.getMessage()).isEqualTo("User is not authorized to view participants for this place.");
        verifyNoInteractions(photoRepository, friendshipRepository);
    }

    @Test
    void updateCustomPlace_ShouldUpdateFields_WhenUserIsCreator() throws Exception {
        // --- ARRANGE ---
        when(customPlaceRepository.findById(placeId)).thenReturn(Optional.of(customPlace));
        when(customPlaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateCustomPlaceRequestDTO request = new UpdateCustomPlaceRequestDTO(
                "Updated Spot", 200, PlaceAccessType.PASSWORD, "secret123",
                true, false, null, OffsetDateTime.now().plusDays(2), true
        );

        // --- EXECUTE ---
        CustomPlace updatedPlace = customPlaceService.updateCustomPlace(placeId, request, null, creator);

        // --- ASSERT ---
        assertThat(updatedPlace.getName()).isEqualTo("Updated Spot");
        assertThat(updatedPlace.getRadiusMeters()).isEqualTo(200);
        assertThat(updatedPlace.getAccessKey()).isEqualTo("secret123");
        assertThat(updatedPlace.isTrending()).isTrue();
        assertThat(updatedPlace.isLive()).isFalse();
    }

    private Point createPoint(double lon, double lat) {
        Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }
}