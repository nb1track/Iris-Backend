package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.ParticipantDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.PlaceAccessType;
import com.iris.backend.service.ChallengeService;
import com.iris.backend.service.CustomPlaceService;
import com.iris.backend.service.GalleryFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class CustomPlaceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private CustomPlaceService customPlaceService;
    @Mock private GalleryFeedService galleryFeedService;
    @Mock private ChallengeService challengeService;

    private CustomPlaceController customPlaceController;

    private User currentUser;
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Manueller Zusammenbau für den echten ObjectMapper
        customPlaceController = new CustomPlaceController(
                customPlaceService, galleryFeedService, challengeService, objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(customPlaceController).build();

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("SpotCreator");
    }

    @Test
    void createCustomPlace_ShouldReturn201Created() throws Exception {
        // --- ARRANGE ---
        CreateCustomPlaceRequestDTO requestDto = new CreateCustomPlaceRequestDTO(
                "Secret Rooftop", 46.9480, 7.4474, 100, PlaceAccessType.PUBLIC,
                null, true, true, null, OffsetDateTime.now().plusDays(1), false
        );
        String requestJson = objectMapper.writeValueAsString(requestDto);

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "spot.jpg", MediaType.IMAGE_JPEG_VALUE, "image data".getBytes());
        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE, requestJson.getBytes());

        CustomPlace mockEntity = new CustomPlace();
        mockEntity.setId(placeId);
        mockEntity.setName("Secret Rooftop");

        GalleryFeedItemDTO responseDto = new GalleryFeedItemDTO(
                GalleryPlaceType.IRIS_SPOT, "Secret Rooftop", 46.9480, 7.4474,
                "https://signed.url/cover.jpg", 0L, null, null, placeId,
                null, 100, "PUBLIC", false, true, null, 0L
        );

        when(customPlaceService.createCustomPlace(any(), any(), any())).thenReturn(mockEntity);
        when(galleryFeedService.getFeedItemForPlace(eq(mockEntity), eq(true))).thenReturn(responseDto);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(multipart("/api/v1/custom-places")
                        .file(imageFile)
                        .file(dataPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Secret Rooftop"))
                .andExpect(jsonPath("$.customPlaceId").value(placeId.toString()));
    }

    @Test
    void getParticipantsForPlace_ShouldReturnList() throws Exception {
        // --- ARRANGE ---
        ParticipantDTO p1 = new ParticipantDTO(UUID.randomUUID(), "User1", "url1", true);
        when(customPlaceService.getParticipants(eq(placeId), any())).thenReturn(List.of(p1));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/custom-places/{placeId}/participants", placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("User1"))
                .andExpect(jsonPath("$[0].isFriend").value(true));
    }

    @Test
    void getParticipantsForPlace_ShouldReturn403_WhenAccessDenied() throws Exception {
        // --- ARRANGE ---
        when(customPlaceService.getParticipants(eq(placeId), any()))
                .thenThrow(new SecurityException("Not authorized"));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/custom-places/{placeId}/participants", placeId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTrendingSpots_ShouldReturnList() throws Exception {
        // --- ARRANGE ---
        GalleryFeedItemDTO spot = new GalleryFeedItemDTO(
                GalleryPlaceType.IRIS_SPOT, "Trending", 0.0, 0.0, null, 5L,
                null, null, UUID.randomUUID(), null, 50, "PUBLIC", true, true, null, 10L
        );
        when(galleryFeedService.getTrendingSpots()).thenReturn(List.of(spot));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/custom-places/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Trending"))
                .andExpect(jsonPath("$[0].isTrending").value(true));
    }

    @Test
    void getMyCreatedSpots_ShouldReturnList() throws Exception {
        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/custom-places/my-spots"))
                .andExpect(status().isOk());

        verify(galleryFeedService).getMyCreatedSpots(any());
    }
}