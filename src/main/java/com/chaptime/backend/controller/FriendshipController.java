package com.chaptime.backend.controller;

import com.chaptime.backend.dto.FriendRequestDTO;
import com.chaptime.backend.dto.FriendshipActionDTO;
import com.chaptime.backend.dto.UserDTO;
import com.chaptime.backend.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/request")
    public ResponseEntity<String> sendFriendRequest(@RequestBody FriendRequestDTO request) {
        try {
            friendshipService.sendFriendRequest(request);
            return ResponseEntity.ok("Friend request sent successfully.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/accept")
    public ResponseEntity<String> acceptFriendRequest(@RequestBody FriendshipActionDTO request) {
        try {
            friendshipService.acceptFriendRequest(request);
            return ResponseEntity.ok("Friendship accepted.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



    @GetMapping
    public ResponseEntity<List<UserDTO>> getFriends(@RequestParam UUID userId) {
        List<UserDTO> friends = friendshipService.getFriendsAsDTO(userId);
        return ResponseEntity.ok(friends);
    }
}