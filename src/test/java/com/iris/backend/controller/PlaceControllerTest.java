package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // Wichtig für OffsetDateTime!
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.ParticipantDTO;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.User;
import com.iris.backend.service.GalleryFeedService;
import com.iris.backend.service.GoogleApiService;
import com.iris.backend.service.PhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private PhotoService photoService;
    @Mock private GalleryFeedService galleryFeedService;
    @Mock private GoogleApiService googleApiService;

    @InjectMocks
    private PlaceController placeController;

    private User currentUser;
    private final UUID customPlaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(placeController).build();

        // Damit der ObjectMapper OffsetDateTime (in den DTOs) versteht!
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("PlaceTester");
    }

    @Test
    void getHistoricalPhotosForGooglePlaceFromOthers_ShouldReturnPhotos() throws Exception {
        // --- ARRANGE ---
        Long googlePlaceId = 123L;
        HistoricalPointDTO point = new HistoricalPointDTO(46.9, 7.4, OffsetDateTime.now());
        HistoricalSearchRequestDTO request = new HistoricalSearchRequestDTO(List.of(point));

        PhotoResponseDTO mockPhoto = new PhotoResponseDTO(
                UUID.randomUUID(), "https://signed.url/1.jpg", OffsetDateTime.now(),
                GalleryPlaceType.GOOGLE_POI, googlePlaceId, null, "Test POI",
                UUID.randomUUID(), "OtherUser", "profile.jpg", 5
        );

        when(photoService.findHistoricalPhotosForGooglePlaceFromOthers(eq(googlePlaceId), anyList(), any()))
                .thenReturn(List.of(mockPhoto));

        String requestJson = objectMapper.writeValueAsString(request);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/places/google-places/{placeId}/historical-photos", googlePlaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeName").value("Test POI"))
                .andExpect(jsonPath("$[0].username").value("OtherUser")); // KORREKTUR: username statt uploaderName
    }

    @Test
    void getMyHistoricalPhotosForCustomPlace_ShouldReturnOnlyMyPhotos() throws Exception {
        // --- ARRANGE ---
        HistoricalPointDTO point = new HistoricalPointDTO(46.9, 7.4, OffsetDateTime.now());
        HistoricalSearchRequestDTO request = new HistoricalSearchRequestDTO(List.of(point));

        PhotoResponseDTO mockPhoto = new PhotoResponseDTO(
                UUID.randomUUID(), "https://signed.url/my_photo.jpg", OffsetDateTime.now(),
                GalleryPlaceType.IRIS_SPOT, null, customPlaceId, "My Spot",
                currentUser.getId(), currentUser.getUsername(), "profile.jpg", 10
        );

        when(photoService.findHistoricalPhotosForCustomPlaceFromUser(eq(customPlaceId), anyList(), any()))
                .thenReturn(List.of(mockPhoto));

        String requestJson = objectMapper.writeValueAsString(request);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/places/custom-places/{placeId}/historical-photos/my-photos", customPlaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("PlaceTester")) // KORREKTUR: username statt uploaderName
                .andExpect(jsonPath("$[0].placeType").value("IRIS_SPOT"));
    }

    @Test
    void getParticipantsForGooglePlace_ShouldReturnParticipants() throws Exception {
        // --- ARRANGE ---
        Long googlePlaceId = 999L;
        ParticipantDTO mockParticipant = new ParticipantDTO(UUID.randomUUID(), "Anna", "anna.jpg", true);

        when(googleApiService.getParticipants(eq(googlePlaceId), any())).thenReturn(List.of(mockParticipant));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/places/google-places/{placeId}/participants", googlePlaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Anna"))
                .andExpect(jsonPath("$[0].isFriend").value(true));
    }

    @Test
    void getTaggablePlaces_ShouldReturnPlacesNearby() throws Exception {
        // --- ARRANGE ---
        GalleryFeedItemDTO mockPlace = new GalleryFeedItemDTO(
                GalleryPlaceType.GOOGLE_POI, "Eiffelturm", 48.8584, 2.2945,
                null, 0L, null, 1L, null, "Paris", 50, null, false, false, null, 0L, null
        );

        when(galleryFeedService.getTaggablePlaces(48.8584, 2.2945)).thenReturn(List.of(mockPlace));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/places/taggable")
                        .param("latitude", "48.8584")
                        .param("longitude", "2.2945"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Eiffelturm"));
    }
}