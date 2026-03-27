package com.iris.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iris.backend.dto.FriendRequestDTO;
import com.iris.backend.dto.FriendshipActionDTO;
import com.iris.backend.dto.LocationReportDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.User;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.service.FriendshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private FriendshipService friendshipService;
    @Mock private GooglePlaceRepository googlePlaceRepository;
    @Mock private CustomPlaceRepository customPlaceRepository;
    @Mock private GeometryFactory geometryFactory;

    @InjectMocks
    private FriendshipController friendshipController;

    private User currentUser;
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(friendshipController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("FriendControllerTester");
    }

    @Test
    void sendFriendRequest_ShouldReturn200Ok_WhenSuccessful() throws Exception {
        // --- ARRANGE ---
        FriendRequestDTO request = new FriendRequestDTO(targetUserId);
        String requestJson = objectMapper.writeValueAsString(request);

        // Wir tun so, als würde der Service einfach ohne Exception durchlaufen
        doNothing().when(friendshipService).sendFriendRequest(any(), eq(targetUserId));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Friend request sent successfully."));
    }

    @Test
    void sendFriendRequest_ShouldReturn400BadRequest_WhenServiceThrowsIllegalState() throws Exception {
        // --- ARRANGE ---
        FriendRequestDTO request = new FriendRequestDTO(targetUserId);
        String requestJson = objectMapper.writeValueAsString(request);

        // Wenn der Service sagt "Die sind schon Freunde", werfen wir eine Exception
        doThrow(new IllegalStateException("A friendship or pending request already exists between these users."))
                .when(friendshipService).sendFriendRequest(any(), eq(targetUserId));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest()) // HTTP 400!
                .andExpect(content().string("A friendship or pending request already exists between these users."));
    }

    @Test
    void getFriendsForShareScreen_ShouldReturnSortedList() throws Exception {
        // --- ARRANGE ---
        UserDTO friend1 = new UserDTO(UUID.randomUUID(), "Anna", "anna.jpg");
        UserDTO friend2 = new UserDTO(UUID.randomUUID(), "Zoe", null);

        when(friendshipService.getFriendsForShareScreen(any())).thenReturn(List.of(friend1, friend2));

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(get("/api/v1/friends/share-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Anna"))
                .andExpect(jsonPath("$[1].username").value("Zoe"));
    }

    @Test
    void reportLocation_ShouldReturn200Ok() throws Exception {
        // --- ARRANGE ---
        LocationReportDTO report = new LocationReportDTO(46.9, 7.4, "token-123");
        String reportJson = objectMapper.writeValueAsString(report);

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/friends/report-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk());

        verify(friendshipService).reportLocationToRequester(any(), any());
    }

    @Test
    void removeFriend_ShouldReturn204NoContent() throws Exception {
        // --- EXECUTE & ASSERT ---
        mockMvc.perform(delete("/api/v1/friends/{friendId}", targetUserId))
                .andExpect(status().isNoContent()); // HTTP 204

        verify(friendshipService).removeFriend(any(), eq(targetUserId));
    }

    @Test
    void acceptFriendRequest_ShouldReturn403Forbidden_WhenSecurityExceptionIsThrown() throws Exception {
        // --- ARRANGE ---
        UUID friendshipId = UUID.randomUUID();
        FriendshipActionDTO request = new FriendshipActionDTO(friendshipId);
        String requestJson = objectMapper.writeValueAsString(request);

        doThrow(new SecurityException("You cannot accept your own friend request."))
                .when(friendshipService).acceptFriendRequest(any(), any());

        // --- EXECUTE & ASSERT ---
        mockMvc.perform(post("/api/v1/friends/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden()) // HTTP 403!
                .andExpect(content().string("You cannot accept your own friend request."));
    }
}