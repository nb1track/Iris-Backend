package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.backend.dto.CheckAllowedRequestDTO;
import com.iris.backend.dto.FcmTokenUpdateRequestDTO;
import com.iris.backend.dto.LocationUpdateRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.User;
import com.iris.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    // Wir simulieren den kompletten Web-Layer (HTTP Requests/Responses)
    private MockMvc mockMvc;

    // Wir wandeln Java-Objekte für den Test in JSON-Strings um
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Wir faken den UserService, da wir dessen Logik ja schon woanders getestet haben!
    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private User mockUser;

    @BeforeEach
    void setUp() {
        // MockMvc mit unserem Controller aufbauen
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();

        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setUsername("ControllerTester");
    }

    @Test
    void getCurrentUserProfile_ShouldReturn200AndUserDTO() throws Exception {
        // --- ARRANGE ---
        UserDTO expectedDto = new UserDTO(mockUser.getId(), "ControllerTester", "https://signed.url/pic.jpg");

        // Wenn der Controller den Service fragt, geben wir unser erwartetes DTO zurück
        // Hinweis: Da @AuthenticationPrincipal im Standalone-Test nicht von Spring Security aufgelöst wird,
        // ist der "User" Parameter in der Mock-Methode hier einfach 'any()'.
        when(userService.getUserProfile(any())).thenReturn(expectedDto);

        // --- EXECUTE & ASSERT ---
        // Wir senden einen simulierten GET Request an den Endpunkt
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk()) // Wir erwarten HTTP 200 OK
                .andExpect(jsonPath("$.username").value("ControllerTester")) // Das JSON muss den Username enthalten
                .andExpect(jsonPath("$.profileImageUrl").value("https://signed.url/pic.jpg"));
    }

    @Test
    void checkAllowed_ShouldReturnTrue_WhenServiceSaysAllowed() throws Exception {
        // --- ARRANGE ---
        CheckAllowedRequestDTO request = new CheckAllowedRequestDTO("+41791234567");
        String requestJson = objectMapper.writeValueAsString(request); // DTO zu JSON machen

        when(userService.checkAllowed("+41791234567")).thenReturn(true);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/users/checkAllowed")
                        .contentType(MediaType.APPLICATION_JSON) // Wir schicken JSON
                        .content(requestJson))                   // Das ist der Body des Requests
                .andExpect(status().isOk())
                .andExpect(content().string("true")); // Wir erwarten "true" als Antwort
    }

    @Test
    void updateFcmToken_ShouldReturn200_WhenRequestIsValid() throws Exception {
        // --- ARRANGE ---
        FcmTokenUpdateRequestDTO request = new FcmTokenUpdateRequestDTO("new-fcm-token-123");
        String requestJson = objectMapper.writeValueAsString(request);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(put("/api/v1/users/me/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        // Verifizieren, dass der Controller die Arbeit an den Service weitergeleitet hat
        verify(userService).updateFcmToken(any(), eq("new-fcm-token-123"));
    }

    @Test
    void updateUserLocation_ShouldReturn200_WhenCalled() throws Exception {
        // --- ARRANGE ---
        LocationUpdateRequestDTO request = new LocationUpdateRequestDTO(46.9, 7.4);
        String requestJson = objectMapper.writeValueAsString(request);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(put("/api/v1/users/me/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(userService).updateUserLocation(any(), any());
    }
}