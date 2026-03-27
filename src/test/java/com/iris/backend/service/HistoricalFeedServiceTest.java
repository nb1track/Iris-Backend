package com.iris.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.repository.HistoricalFeedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricalFeedServiceTest {

    @Mock private HistoricalFeedRepository historicalFeedRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private GcsStorageService gcsStorageService;

    @InjectMocks
    private HistoricalFeedService historicalFeedService;

    private final String PHOTOS_BUCKET = "test-photos-bucket";

    @BeforeEach
    void setUp() {
        // Bucket-Name injizieren (entspricht dem @Value Feld im Service)
        ReflectionTestUtils.setField(historicalFeedService, "photosBucketName", PHOTOS_BUCKET);
    }

    @Test
    void generateHistoricalFeed_ShouldReturnEmptyList_WhenHistoryIsEmpty() {
        // KORREKTUR: Die Methode heißt generateHistoricalFeed!
        List<GalleryFeedItemDTO> result = historicalFeedService.generateHistoricalFeed(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(historicalFeedRepository);
    }

    @Test
    void generateHistoricalFeed_ShouldMapProjectionsToDTOsAndGenerateSignedUrls() throws Exception {
        // --- ARRANGE ---
        HistoricalPointDTO point = new HistoricalPointDTO(46.9480, 7.4474, OffsetDateTime.now());
        List<HistoricalPointDTO> history = List.of(point);
        String mockJson = "[{\"latitude\":46.9480, \"longitude\":7.4474}]";

        when(objectMapper.writeValueAsString(history)).thenReturn(mockJson);

        // 2. Mocking der Repository-Projektion
        HistoricalFeedRepository.GalleryFeedItemDTOProjection mockProjection = mock(HistoricalFeedRepository.GalleryFeedItemDTOProjection.class);
        when(mockProjection.getName()).thenReturn("Zytglogge Bern");
        when(mockProjection.getPlaceType()).thenReturn(GalleryPlaceType.GOOGLE_POI);
        when(mockProjection.getCoverImageUrl()).thenReturn("raw-image.jpg");
        when(mockProjection.getPhotoCount()).thenReturn(10L);
        when(mockProjection.getParticipantCount()).thenReturn(3L);
        when(mockProjection.getNewestPhotoTimestamp()).thenReturn(Instant.now());

        when(historicalFeedRepository.findHistoricalFeed(mockJson)).thenReturn(List.of(mockProjection));

        when(gcsStorageService.generateSignedUrl(eq(PHOTOS_BUCKET), eq("raw-image.jpg"), anyLong(), any()))
                .thenReturn("https://signed-url.com/image.jpg");

        // --- EXECUTE ---
        // KORREKTUR: Aufruf der richtigen Methode
        List<GalleryFeedItemDTO> result = historicalFeedService.generateHistoricalFeed(history);

        // --- ASSERT ---
        assertThat(result).hasSize(1);
        GalleryFeedItemDTO item = result.get(0);

        assertThat(item.name()).isEqualTo("Zytglogge Bern");
        assertThat(item.coverImageUrl()).isEqualTo("https://signed-url.com/image.jpg");
    }

    @Test
    void generateHistoricalFeed_ShouldThrowRuntimeException_WhenJsonProcessingFails() throws Exception {
        // --- ARRANGE ---
        HistoricalPointDTO point = new HistoricalPointDTO(46.9480, 7.4474, OffsetDateTime.now());
        List<HistoricalPointDTO> history = List.of(point);

        when(objectMapper.writeValueAsString(history)).thenThrow(mock(JsonProcessingException.class));

        // --- EXECUTE & ASSERT ---
        assertThrows(RuntimeException.class, () -> {
            historicalFeedService.generateHistoricalFeed(history); // KORREKTUR hier
        });
    }
}