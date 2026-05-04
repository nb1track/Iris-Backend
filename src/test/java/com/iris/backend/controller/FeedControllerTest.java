package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.service.HistoricalFeedService;
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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private HistoricalFeedService historicalFeedService;

    @InjectMocks
    private FeedController feedController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(feedController).build();

        // ObjectMapper konfigurieren, damit er OffsetDateTime in den DTOs versteht
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getHistoricalFeed_ShouldReturn200AndFeedItems() throws Exception {
        // --- ARRANGE ---
        // 1. Wir erstellen eine fiktive Historie
        HistoricalPointDTO point = new HistoricalPointDTO(46.9480, 7.4474, OffsetDateTime.now());
        HistoricalSearchRequestDTO request = new HistoricalSearchRequestDTO(List.of(point));
        String requestJson = objectMapper.writeValueAsString(request);

        // 2. Wir erstellen ein fiktives Ergebnis-Item für den Feed
        GalleryFeedItemDTO mockItem = new GalleryFeedItemDTO(
                GalleryPlaceType.GOOGLE_POI, "Zytglogge", 46.9480, 7.4474,
                "https://signed.url/image.jpg", 15L, OffsetDateTime.now(),
                1L, null, "Bärenplatz, Bern", null, null, false, false, null, 3L, null
        );

        when(historicalFeedService.generateHistoricalFeed(anyList())).thenReturn(List.of(mockItem));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/feed/historical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Zytglogge"))
                .andExpect(jsonPath("$[0].photoCount").value(15))
                .andExpect(jsonPath("$[0].placeType").value("GOOGLE_POI"));
    }
}