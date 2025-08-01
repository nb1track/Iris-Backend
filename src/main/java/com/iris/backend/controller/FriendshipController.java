package com.iris.backend.controller;

import com.iris.backend.dto.FriendRequestDTO;
import com.iris.backend.dto.FriendshipActionDTO;
import com.iris.backend.dto.PendingRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.Place;
import com.iris.backend.model.User;
import com.iris.backend.repository.PlaceRepository;
import com.iris.backend.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final PlaceRepository placeRepository;

    /**
     * Constructor for FriendshipController.
     *
     * @param friendshipService the service used to handle friendship-related operations
     */
    public FriendshipController(FriendshipService friendshipService, PlaceRepository placeRepository) {
        this.friendshipService = friendshipService;
        this.placeRepository = placeRepository;
    }

    /**
     * Handles sending a friend request from the authenticated user to another user specified by their ID.
     * The method validates the location and proximity of both users before processing the request.
     *
     * @param requester the currently authenticated user sending the friend request, extracted from the authentication token
     * @param request the data transfer object containing the ID of the user who will receive the friend request
     * @return a ResponseEntity containing a success message if the request was sent successfully,
     *         or an error message with the appropriate HTTP status if an exception occurs
     */
    @PostMapping("/request")
    public ResponseEntity<String> sendFriendRequest(
            @AuthenticationPrincipal User requester, // Holt den User aus dem Token
            @RequestBody FriendRequestDTO request) {
        try {
            // Übergibt den angemeldeten User und die ID des Empfängers an den Service
            friendshipService.sendFriendRequest(requester, request.addresseeId());
            return ResponseEntity.ok("Friend request sent successfully.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Handles accepting a pending friendship request by an authenticated user.
     *
     * @param acceptor the currently authenticated user accepting the friend request, extracted from the authentication token
     * @param request the data transfer object containing the ID of the friendship to be accepted
     * @return a ResponseEntity containing a success message if the request was accepted successfully,
     *         a bad request message if the request is invalid, or a forbidden message for security violations
     */
    @PostMapping("/accept")
    public ResponseEntity<String> acceptFriendRequest(
            @AuthenticationPrincipal User acceptor, // Angemeldeten User holen
            @RequestBody FriendshipActionDTO request) {
        try {
            friendshipService.acceptFriendRequest(request, acceptor); // User an Service übergeben
            return ResponseEntity.ok("Friendship accepted.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * Retrieves a list of pending friend requests for the currently authenticated user.
     *
     * @param currentUser the authenticated user for whom the pending requests are being retrieved, extracted from the authentication token
     * @return a ResponseEntity containing a list of PendingRequestDTO objects representing the user's pending friend requests
     */
    @GetMapping("/requests/pending")
    public ResponseEntity<List<PendingRequestDTO>> getPendingRequests(@AuthenticationPrincipal User currentUser) {
        List<PendingRequestDTO> requests = friendshipService.getPendingRequests(currentUser);
        return ResponseEntity.ok(requests);
    }
    /**
     * Retrieves a list of friends for the currently authenticated user.
     *
     * @param user the authenticated user whose friends are being retrieved, extracted from the authentication token
     * @return a ResponseEntity containing a list of UserDTO objects representing the user's friends,
     *         or an unauthorized status if the user is not authenticated
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getFriends(@AuthenticationPrincipal User user) {
        // @AuthenticationPrincipal injiziert den User, der durch das Token authentifiziert wurde
        if (user == null) {
            // Sollte nie passieren, wenn der Security-Filter korrekt arbeitet, aber eine gute Absicherung
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Wir benutzen die ID des angemeldeten Users
        List<UserDTO> friends = friendshipService.getFriendsAsDTO(user.getId());
        return ResponseEntity.ok(friends);
    }

    /**
     * Retrieves a list of friends of the authenticated user who are near a specified place.
     *
     * This method finds the specified place by its ID, then fetches and returns
     * a list of friends that are geographically close to that place.
     *
     * @param currentUser the authenticated user making the request
     * @param placeId the unique identifier of the place to search for nearby friends
     * @return a ResponseEntity containing a list of UserDTO objects that represent the nearby friends
     */
    @GetMapping("/at-place")
    public ResponseEntity<List<UserDTO>> getFriendsAtPlace(
            @AuthenticationPrincipal User currentUser,
            @RequestParam Long placeId) {

        // Finde den Ort anhand der ID
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RuntimeException("Place not found"));

        // Finde die nahen Freunde an diesem Ort
        List<UserDTO> nearbyFriends = friendshipService.findNearbyFriendsAtPlace(currentUser, place.getLocation());
        return ResponseEntity.ok(nearbyFriends);
    }

    /**
     * Handles rejecting a pending friendship request.
     * The entire friendship entry is deleted from the database.
     *
     * @param currentUser The currently authenticated user rejecting the request.
     * @param request The DTO containing the ID of the friendship to be rejected.
     * @return A ResponseEntity indicating success or failure.
     */
    @PostMapping("/reject")
    public ResponseEntity<String> rejectFriendRequest(
            @AuthenticationPrincipal User currentUser,
            @RequestBody FriendshipActionDTO request) { // Wir können das gleiche DTO wie für "accept" wiederverwenden
        try {
            friendshipService.rejectFriendRequest(request.friendshipId(), currentUser);
            return ResponseEntity.ok("Friendship request rejected and deleted.");
        } catch (SecurityException e) {
            // Wenn der User nicht berechtigt ist, die Anfrage abzulehnen
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (RuntimeException e) {
            // Wenn die Freundschaftsanfrage nicht gefunden wurde
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}