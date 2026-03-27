package com.iris.backend.service;

import com.iris.backend.dto.PhotoUploadRequestDTO;
import com.iris.backend.dto.PhotoUploadResponse;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PhotoVisibility;
import com.iris.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoRepository photoRepository;
    @Mock private GooglePlaceRepository googlePlaceRepository;
    @Mock private CustomPlaceRepository customPlaceRepository;
    @Mock private GcsStorageService gcsStorageService;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipService friendshipService;
    @Mock private FcmService fcmService;
    @Mock private ChallengeService challengeService;
    @Mock private PhotoLikeRepository photoLikeRepository;

    @InjectMocks
    private PhotoService photoService;

    private User testUser;
    private Photo testPhoto;
    private final UUID testPhotoId = UUID.randomUUID();
    private final String PHOTOS_BUCKET = "iris-test-photos-bucket";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(photoService, "photosBucketName", PHOTOS_BUCKET);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("Photographer");

        testPhoto = new Photo();
        testPhoto.setId(testPhotoId);
        testPhoto.setStorageUrl("my-cool-photo.jpg");
        testPhoto.setUploader(testUser);
    }

    @Test
    void deletePhoto_ShouldDeleteFromCloudStorageAndDatabase() {
        when(photoRepository.findById(testPhotoId)).thenReturn(Optional.of(testPhoto));

        photoService.deletePhoto(testPhotoId, testUser);

        verify(gcsStorageService).deleteFile(PHOTOS_BUCKET, "my-cool-photo.jpg");
        verify(photoRepository).delete(testPhoto);
    }

    @Test
    void uploadPhotos_ShouldUploadToCloudAndSaveToDatabase() throws Exception {
        // --- ARRANGE ---
        MultipartFile mockFile1 = mock(MultipartFile.class);
        MultipartFile mockFile2 = mock(MultipartFile.class);
        MultipartFile[] files = {mockFile1, mockFile2};

        // KORREKTUR: Die Parameter passen jetzt exakt zum PhotoUploadRequestDTO Record!
        // Parameter: lat, lon, visibility, googlePlaceId, customPlaceId, friendIds (List), challengeId (UUID)
        PhotoUploadRequestDTO req1 = new PhotoUploadRequestDTO(
                46.9, 7.4, PhotoVisibility.PUBLIC, 1L, null, null, null
        );

        UUID customPlaceId = UUID.randomUUID();
        PhotoUploadRequestDTO req2 = new PhotoUploadRequestDTO(
                47.0, 7.5, PhotoVisibility.FRIENDS, null, customPlaceId, null, null
        );
        List<PhotoUploadRequestDTO> requests = List.of(req1, req2);

        GooglePlace mockGooglePlace = new GooglePlace();
        mockGooglePlace.setId(1L);
        when(googlePlaceRepository.findById(1L)).thenReturn(Optional.of(mockGooglePlace));

        CustomPlace mockCustomPlace = new CustomPlace();
        mockCustomPlace.setId(customPlaceId);
        when(customPlaceRepository.findById(customPlaceId)).thenReturn(Optional.of(mockCustomPlace));

        when(gcsStorageService.uploadPhoto(any(MultipartFile.class)))
                .thenReturn("https://storage.fake.com/photo.jpg");

        Photo savedPhoto1 = new Photo(); savedPhoto1.setId(UUID.randomUUID());
        savedPhoto1.setVisibility(PhotoVisibility.PUBLIC); // Wichtig für den FcmService-Check

        Photo savedPhoto2 = new Photo(); savedPhoto2.setId(UUID.randomUUID());
        savedPhoto2.setVisibility(PhotoVisibility.FRIENDS); // Wichtig für den FcmService-Check

        when(photoRepository.save(any(Photo.class))).thenReturn(savedPhoto1, savedPhoto2);

        // Da Foto 2 'FRIENDS' ist, ruft der Service die Freundesliste ab und sendet Notifications.
        // Wir faken diese Methoden, damit sie nicht ins Leere laufen.
        when(friendshipService.getFriendsAsEntities(testUser.getId())).thenReturn(List.of());

        // --- EXECUTE ---
        List<PhotoUploadResponse> responses = photoService.uploadPhotos(files, requests, testUser);

        // --- ASSERT ---
        assertThat(responses).hasSize(2);

        // Die Signatur im GcsStorageService lautet anscheinend nur uploadPhoto(file) ohne Bucket-Namen als 1. Parameter.
        verify(gcsStorageService, times(2)).uploadPhoto(any(MultipartFile.class));
        verify(photoRepository, times(2)).save(any(Photo.class));
    }

    @Test
    void deletePhoto_ShouldThrowException_WhenUserIsNotUploader() {
        // --- ARRANGE ---
        User maliciousUser = new User();
        maliciousUser.setId(UUID.randomUUID()); // Eine ANDERE ID als der testUser!
        maliciousUser.setUsername("Hacker");

        when(photoRepository.findById(testPhotoId)).thenReturn(Optional.of(testPhoto));

        // --- EXECUTE & ASSERT ---
        // Wenn ein Hacker versucht, das Foto von jemand anderem zu löschen, muss es knallen!
        assertThrows(SecurityException.class, () -> {
            photoService.deletePhoto(testPhotoId, maliciousUser);
        });

        // Sicherstellen, dass NIE gelöscht wurde!
        verify(gcsStorageService, never()).deleteFile(anyString(), anyString());
        verify(photoRepository, never()).delete(any());
    }
}