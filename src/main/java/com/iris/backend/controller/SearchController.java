package com.iris.backend.controller;

import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.User;
import com.iris.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final UserService userService;

    public SearchController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @RequestParam String query,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Page<UserDTO> results = userService.searchUsers(query, currentUser, pageable);
        return ResponseEntity.ok(results);
    }
}