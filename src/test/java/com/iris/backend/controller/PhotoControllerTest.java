package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iris.backend.dto.PhotoResponseDTO;
import com.iris.backend.dto.PhotoUploadRequestDTO;
import com.iris.backend.dto.feed.GalleryPlaceType;
import com.iris.backend.model.User;
import com.iris.backend.repository.UserRepository;
import com.iris.backend.service.PhotoLikeService;
import com.iris.backend.service.PhotoService;
import com.iris.backend.model.enums.PhotoVisibility;
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
class PhotoControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private PhotoService photoService;
    @Mock private PhotoLikeService photoLikeService;
    @Mock private UserRepository userRepository;

    // Kein @InjectMocks hier, wir bauen ihn manuell, um ihm den ECHTEN ObjectMapper zu geben!
    private PhotoController photoController;

    private User currentUser;
    private final UUID photoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Echten ObjectMapper erstellen, der Datum/Zeit versteht
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Controller manuell zusammenbauen
        photoController = new PhotoController(photoService, photoLikeService, userRepository, objectMapper);

        // MockMvc für simulierte HTTP-Requests initialisieren
        mockMvc = MockMvcBuilders.standaloneSetup(photoController).build();

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("PhotoUploader");
    }

    @Test
    void uploadPhoto_ShouldReturn201Created_WhenValid() throws Exception {
        // --- ARRANGE ---
        // 1. Wir faken eine hochgeladene JPG-Datei
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy image bytes".getBytes()
        );

        // 2. Wir faken den Metadaten-Teil als JSON-String, genau wie das Flutter-Frontend es senden würde
        PhotoUploadRequestDTO metadataDto = new PhotoUploadRequestDTO(
                46.9480, 7.4474, PhotoVisibility.PUBLIC, 123L, null, null, null
        );
        String metadataJson = objectMapper.writeValueAsString(metadataDto);
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata", "", MediaType.APPLICATION_JSON_VALUE, metadataJson.getBytes()
        );

        // Wir sagen dem Service, er soll eine zufällige UUID als neues Foto zurückgeben
        UUID newPhotoId = UUID.randomUUID();
        when(photoService.createPhoto(any(), eq(46.9480), eq(7.4474), eq(PhotoVisibility.PUBLIC),
                eq(123L), isNull(), any(), isNull(), isNull())).thenReturn(newPhotoId);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(multipart("/api/v1/photos")
                        .file(file)
                        .file(metadataPart))
                .andExpect(status().isCreated()) // HTTP 201
                .andExpect(jsonPath("$.photoId").value(newPhotoId.toString()));
    }

    @Test
    void getPhotosByIds_ShouldReturnListOfPhotos() throws Exception {
        // --- ARRANGE ---
        List<UUID> requestIds = List.of(photoId);
        String requestJson = objectMapper.writeValueAsString(requestIds);

        PhotoResponseDTO responseDto = new PhotoResponseDTO(
                photoId, "https://signed.url", OffsetDateTime.now(),
                GalleryPlaceType.GOOGLE_POI, 123L, null, "Bern",
                currentUser.getId(), "PhotoUploader", "profile.jpg", 10
        );

        when(photoService.getPhotoDTOsByIds(eq(requestIds), any())).thenReturn(List.of(responseDto));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(jsonPath("$[0].photoId").value(photoId.toString()))
                .andExpect(jsonPath("$[0].username").value("PhotoUploader"));
    }

    @Test
    void deletePhoto_ShouldReturn204NoContent_WhenAuthorized() throws Exception {
        // --- EXECUTE & ASSERT ---
        // Wir simulieren einen DELETE-Request
        mockMvc.perform(delete("/api/v1/photos/{photoId}", photoId))
                .andExpect(status().isNoContent()); // HTTP 204

        verify(photoService).deletePhoto(eq(photoId), any());
    }

    @Test
    void deletePhoto_ShouldReturn403Forbidden_WhenSecurityExceptionIsThrown() throws Exception {
        // --- ARRANGE ---
        // Wenn ein Hacker versucht zu löschen, wirft der Service eine SecurityException
        doThrow(new SecurityException("Not authorized")).when(photoService).deletePhoto(eq(photoId), any());

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(delete("/api/v1/photos/{photoId}", photoId))
                .andExpect(status().isForbidden()); // HTTP 403!
    }

    @Test
    void toggleLikeOnPhoto_ShouldReturn200Ok() throws Exception {
        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/photos/{photoId}/toggle-like", photoId))
                .andExpect(status().isOk());

        verify(photoLikeService).toggleLike(eq(photoId), any());
    }
}