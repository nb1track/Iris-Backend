package com.iris.backend.controller;

import com.iris.backend.dto.FriendRequestDTO;
import com.iris.backend.dto.FriendshipActionDTO;
import com.iris.backend.dto.PendingRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.GooglePlace;
import com.iris.backend.model.User;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.GooglePlaceRepository;
import com.iris.backend.service.FriendshipService;
import org.locationtech.jts.geom.Point;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final GooglePlaceRepository googlePlaceRepository;
    private final CustomPlaceRepository customPlaceRepository; // NEU

    public FriendshipController(
            FriendshipService friendshipService,
            GooglePlaceRepository googlePlaceRepository,
            CustomPlaceRepository customPlaceRepository // NEU
    ) {
        this.friendshipService = friendshipService;
        this.googlePlaceRepository = googlePlaceRepository;
        this.customPlaceRepository = customPlaceRepository; // NEU
    }

    /**
     * Retrieves a list of friends of the authenticated user who are near a specified place.
     * This endpoint now flexibly handles both Google Places and Custom Places.
     *
     * @param currentUser The authenticated user.
     * @param googlePlaceId The ID of the Google Place (optional).
     * @param customPlaceId The ID of the Custom Place (optional).
     * @return A list of UserDTOs representing nearby friends.
     */
    @GetMapping("/at-place")
    public ResponseEntity<List<UserDTO>> getFriendsAtPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Long googlePlaceId,
            @RequestParam(required = false) UUID customPlaceId) {

        // Validierung: Es muss genau eine der beiden IDs angegeben werden.
        if ((googlePlaceId == null && customPlaceId == null) || (googlePlaceId != null && customPlaceId != null)) {
            return ResponseEntity.badRequest().build();
        }

        Point placeLocation;

        if (googlePlaceId != null) {
            // Logik, um die Location von einem GooglePlace zu holen
            GooglePlace googlePlace = googlePlaceRepository.findById(googlePlaceId)
                    .orElseThrow(() -> new RuntimeException("GooglePlace not found with ID: " + googlePlaceId));
            placeLocation = googlePlace.getLocation();
        } else {
            // Logik, um die Location von einem CustomPlace zu holen
            CustomPlace customPlace = customPlaceRepository.findById(customPlaceId)
                    .orElseThrow(() -> new RuntimeException("CustomPlace not found with ID: " + customPlaceId));
            placeLocation = customPlace.getLocation();
        }

        // Der Aufruf an den Service bleibt unverändert, da dieser nur eine Location erwartet.
        List<UserDTO> nearbyFriends = friendshipService.findNearbyFriendsAtPlace(currentUser, placeLocation);
        return ResponseEntity.ok(nearbyFriends);
    }


    @PostMapping("/request")
    public ResponseEntity<String> sendFriendRequest(
            @AuthenticationPrincipal User requester,
            @RequestBody FriendRequestDTO request) {
        try {
            friendshipService.sendFriendRequest(requester, request.addresseeId());
            return ResponseEntity.ok("Friend request sent successfully.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/accept")
    public ResponseEntity<String> acceptFriendRequest(
            @AuthenticationPrincipal User acceptor,
            @RequestBody FriendshipActionDTO request) {
        try {
            friendshipService.acceptFriendRequest(request, acceptor);
            return ResponseEntity.ok("Friendship accepted.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<List<PendingRequestDTO>> getPendingRequests(@AuthenticationPrincipal User currentUser) {
        List<PendingRequestDTO> requests = friendshipService.getPendingRequests(currentUser);
        return ResponseEntity.ok(requests);
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getFriends(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UserDTO> friends = friendshipService.getFriendsAsDTO(user.getId());
        return ResponseEntity.ok(friends);
    }

    @PostMapping("/reject")
    public ResponseEntity<String> rejectFriendRequest(
            @AuthenticationPrincipal User currentUser,
            @RequestBody FriendshipActionDTO request) {
        try {
            friendshipService.rejectFriendRequest(request.friendshipId(), currentUser);
            return ResponseEntity.ok("Friendship request rejected and deleted.");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID friendId) {
        friendshipService.removeFriend(currentUser, friendId);
        return ResponseEntity.noContent().build();
    }
}