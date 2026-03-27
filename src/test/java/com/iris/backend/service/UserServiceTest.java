package com.iris.backend.service;

import com.google.firebase.auth.FirebaseToken;
import com.iris.backend.dto.SignUpRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.repository.BlockedNumberRepository;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.PhotoRepository;
import com.iris.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// WICHTIG: Kein @SpringBootTest, kein @Testcontainers!
// Wir nutzen nur Mockito für rasante Unit-Tests.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private BlockedNumberRepository blockedNumberRepository;
    @Mock private GcsStorageService gcsStorageService;

    // Der Service, den wir WIRKLICH testen
    private UserService userService;

    private final String PHOTOS_BUCKET = "test-photos-bucket";
    private final String PROFILES_BUCKET = "test-profiles-bucket";

    private User testUser;
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Wir injizieren die Mocks manuell in den Service
        userService = new UserService(
                userRepository,
                photoRepository,
                friendshipRepository,
                blockedNumberRepository,
                gcsStorageService,
                PHOTOS_BUCKET,
                PROFILES_BUCKET
        );

        // Ein Standard-Testuser, den wir in mehreren Tests brauchen können
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setUsername("Tester");
        testUser.setFirebaseUid("firebase-uid-123");
        testUser.setProfileImageUrl("my-profile-pic.jpg");
    }

    @Test
    void checkAllowed_ShouldReturnTrue_WhenNumberIsNotBlocked() {
        // --- ARRANGE ---
        String phone = "+41791234567";
        // Wir faken das Repo: Wenn nach dieser Nummer gesucht wird, gib 'false' (nicht blockiert) zurück
        when(blockedNumberRepository.existsByPhoneNumber(phone)).thenReturn(false);

        // --- EXECUTE ---
        boolean isAllowed = userService.checkAllowed(phone);

        // --- ASSERT ---
        assertThat(isAllowed).isTrue();
    }

    @Test
    void getUserProfile_ShouldGenerateSignedUrl_WhenProfileImageExists() {
        // --- ARRANGE ---
        // Wir faken den Cloud Storage: Wenn generateSignedUrl aufgerufen wird, gib eine Dummy-URL zurück
        when(gcsStorageService.generateSignedUrl(PROFILES_BUCKET, "my-profile-pic.jpg", 15, TimeUnit.MINUTES))
                .thenReturn("https://fake-gcs-url.com/my-profile-pic.jpg");

        // --- EXECUTE ---
        UserDTO result = userService.getUserProfile(testUser);

        // --- ASSERT ---
        assertThat(result.username()).isEqualTo("Tester");
        assertThat(result.profileImageUrl()).isEqualTo("https://fake-gcs-url.com/my-profile-pic.jpg");
    }

    @Test
    void deleteUserAccount_ShouldDeletePhotosAndProfileImageFromStorage() {
        // --- ARRANGE ---
        Photo testPhoto = new Photo();
        testPhoto.setStorageUrl("photo1.jpg");

        // Wenn der UserService nach dem User sucht, geben wir den TestUser zurück
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        // Wenn er nach den Fotos des Users sucht, geben wir eine Liste mit einem Foto zurück
        when(photoRepository.findAllByUploader(testUser)).thenReturn(List.of(testPhoto));

        // --- EXECUTE ---
        userService.deleteUserAccount(testUserId);

        // --- ASSERT ---
        // Wir prüfen (verify), ob der Storage-Service genau mit diesen Daten aufgerufen wurde!
        verify(gcsStorageService).deleteFile(PHOTOS_BUCKET, "photo1.jpg");
        verify(gcsStorageService).deleteFile(PROFILES_BUCKET, "my-profile-pic.jpg");
        // Und wir prüfen, ob der User wirklich aus der DB gelöscht wurde
        verify(userRepository).delete(testUser);
    }

    @Test
    void registerNewUser_ShouldThrowException_WhenUserAlreadyExists() {
        // --- ARRANGE ---
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("firebase-uid-123");

        SignUpRequestDTO requestDTO = new SignUpRequestDTO("NewUser", "First", "Last", null, null);

        // Wir simulieren, dass der User in der Datenbank BEREITS EXISTIERT
        when(userRepository.findByFirebaseUid("firebase-uid-123")).thenReturn(Optional.of(testUser));

        // --- EXECUTE & ASSERT ---
        // Wir erwarten, dass der Service eine IllegalStateException wirft
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            userService.registerNewUser(mockToken, requestDTO);
        });

        assertThat(exception.getMessage()).isEqualTo("User already exists in our database.");

        // Verifiziere, dass userRepository.save NIEMALS aufgerufen wurde
        verify(userRepository, never()).save(any());
    }
}