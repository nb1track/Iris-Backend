package com.iris.backend.service;

import com.iris.backend.dto.FriendshipActionDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private GcsStorageService gcsStorageService;
    @Mock private FcmService fcmService;
    @Mock private GooglePlaceRepository googlePlaceRepository;
    @Mock private CustomPlaceRepository customPlaceRepository;
    @Mock private GalleryFeedService galleryFeedService;

    @InjectMocks
    private FriendshipService friendshipService;

    private User currentUser;
    private User friendUser;
    private final String PROFILES_BUCKET = "test-profiles-bucket";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(friendshipService, "profileImagesBucketName", PROFILES_BUCKET);

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("Current");

        friendUser = new User();
        friendUser.setId(UUID.randomUUID());
        friendUser.setUsername("Friend");
        friendUser.setProfileImageUrl("friend-pic.jpg");
    }

    @Test
    void getFriendsAsDTO_ShouldReturnMappedFriendsWithSignedUrls() {
        // --- ARRANGE ---
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));

        Friendship mockFriendship = new Friendship();
        mockFriendship.setUserOne(currentUser);
        mockFriendship.setUserTwo(friendUser);
        mockFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByUserOneAndStatusOrUserTwoAndStatus(
                currentUser, FriendshipStatus.ACCEPTED, currentUser, FriendshipStatus.ACCEPTED
        )).thenReturn(List.of(mockFriendship));

        // Fake die URL Generierung
        when(gcsStorageService.generateSignedUrl(PROFILES_BUCKET, "friend-pic.jpg", 15, TimeUnit.MINUTES))
                .thenReturn("https://signed.com/friend-pic.jpg");

        // --- EXECUTE ---
        List<UserDTO> friends = friendshipService.getFriendsAsDTO(currentUser.getId());

        // --- ASSERT ---
        assertThat(friends).hasSize(1);
        UserDTO dto = friends.get(0);
        assertThat(dto.username()).isEqualTo("Friend");
        assertThat(dto.profileImageUrl()).isEqualTo("https://signed.com/friend-pic.jpg");
    }

    @Test
    void sendFriendRequest_ShouldSaveNewPendingFriendship() {
        // --- ARRANGE ---
        when(userRepository.findById(friendUser.getId())).thenReturn(Optional.of(friendUser));

        // Wir tun so, als gäbe es noch keine Beziehung zwischen den beiden
        when(friendshipRepository.existsByUserOneAndUserTwo(any(), any())).thenReturn(false);

        // --- EXECUTE ---
        friendshipService.sendFriendRequest(currentUser, friendUser.getId());

        // --- ASSERT ---
        // Prüfen, ob eine neue Freundschaft (PENDING) gespeichert wurde
        verify(friendshipRepository).save(argThat(friendship ->
                friendship.getStatus() == FriendshipStatus.PENDING &&
                        friendship.getActionUser().equals(currentUser)
        ));
    }

    @Test
    void sendFriendRequest_ShouldThrowException_WhenRequestAlreadyExists() {
        // --- ARRANGE ---
        when(userRepository.findById(friendUser.getId())).thenReturn(Optional.of(friendUser));

        // Wir tun so, als GÄBE es schon eine Anfrage
        when(friendshipRepository.existsByUserOneAndUserTwo(any(), any())).thenReturn(true);

        // --- EXECUTE & ASSERT ---
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            friendshipService.sendFriendRequest(currentUser, friendUser.getId());
        });

        assertThat(exception.getMessage()).isEqualTo("A friendship or pending request already exists between these users.");
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void acceptFriendRequest_ShouldThrowException_WhenAcceptingOwnRequest() {
        // --- ARRANGE ---
        UUID friendshipId = UUID.randomUUID();
        Friendship pendingRequest = new Friendship();
        pendingRequest.setStatus(FriendshipStatus.PENDING);
        // ACHTUNG: Der CurrentUser hat die Anfrage gesendet!
        pendingRequest.setActionUser(currentUser);

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(pendingRequest));
        FriendshipActionDTO dto = new FriendshipActionDTO(friendshipId);

        // --- EXECUTE & ASSERT ---
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            friendshipService.acceptFriendRequest(dto, currentUser);
        });

        assertThat(exception.getMessage()).isEqualTo("You cannot accept your own friend request.");
    }
}