package com.iris.backend.controller;

import com.iris.backend.dto.*;
import com.iris.backend.model.User;
import com.iris.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.slf4j.Logger; // NEU
import org.slf4j.LoggerFactory; // NEU
import java.util.List;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class); // NEU

    /**
     * Constructs a new UserController instance with the specified UserService.
     *
     * @param userService the service used to manage user-related operations
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Updates the location of the currently authenticated user.
     *
     * @param user the currently authenticated user, extracted from the security context
     * @param locationUpdate the new location data containing latitude and longitude values
     * @return a {@code ResponseEntity} with status 200 (OK) if the location was successfully updated
     */
    @PutMapping("/me/location")
    public ResponseEntity<Void> updateUserLocation(
            @AuthenticationPrincipal User user, // Holt den User sicher aus dem Token
            @RequestBody LocationUpdateRequestDTO locationUpdate) {

        // Wir benutzen die ID des angemeldeten Benutzers
        userService.updateUserLocation(user.getId(), locationUpdate);
        return ResponseEntity.ok().build();
    }

    /**
     * Exports the user data for the currently authenticated user.
     *
     * @param user the currently authenticated user, extracted from the security context
     * @return a {@code ResponseEntity} containing a {@code UserDataExportDTO} object with the exported data of the user
     */
    @GetMapping("/me/export")
    public ResponseEntity<UserDataExportDTO> exportUserData(@AuthenticationPrincipal User user) { // BENUTZE @AuthenticationPrincipal
        UserDataExportDTO exportData = userService.exportUserData(user.getId());
        return ResponseEntity.ok(exportData);
    }

    /**
     * Deletes the account of the currently authenticated user.
     *
     * @param user the currently authenticated user, extracted from the security context
     * @return a {@code ResponseEntity} with status 204 (No Content) if the user account was successfully deleted
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal User user) { // BENUTZE @AuthenticationPrincipal
        userService.deleteUserAccount(user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Registers a new user in the system using Firebase authentication and user-provided signup information.
     * Verifies the provided Firebase ID token and saves the user if valid and unique.
     *
     * @param authHeader the HTTP Authorization header containing the Firebase token for verification
     * @param signUpRequest an object containing the username for the new user
     * @return a {@code ResponseEntity} containing a {@code UserDTO} with the new user's details
     *         and a status of 201 (Created) if successful, 409 (Conflict) if the user already exists,
     *         or 401 (Unauthorized) if the token is invalid or missing
     */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid SignUpRequestDTO signUpRequest) { // @Valid aktiviert die @NotBlank Checks im DTO

        logger.info("====== SIGNUP ENDPOINT REACHED for user: {} ======", signUpRequest.username());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String idToken = authHeader.substring(7);

        try {
            // 1. Token verifizieren
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

            // 2. User registrieren (Service kümmert sich um alle Felder)
            User newUser = userService.registerNewUser(decodedToken, signUpRequest);

            // 3. Antwort bauen
            UserDTO userDTO = new UserDTO(newUser.getId(), newUser.getUsername(), newUser.getProfileImageUrl());
            return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);

        } catch (FirebaseAuthException e) {
            logger.error("!!! FIREBASE TOKEN VERIFICATION FAILED !!!", e);
            String errorMessage = "Firebase Auth Error: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMessage);

        } catch (IllegalStateException e) {
            // User existiert bereits
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during signup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    /**
     * Finds and returns a list of users near a given geographical location.
     * The location and search radius are provided as request parameters.
     *
     * @param currentUser    The currently authenticated user, to exclude them from the results.
     * @param latitude       The latitude of the location to search around.
     * @param longitude      The longitude of the location to search around.
     * @param radius         The search radius in meters. Defaults to 5000 meters.
     * @return A ResponseEntity containing a list of UserDTOs.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<UserDTO>> getNearbyUsers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(name = "radius", defaultValue = "5000") double radius) {

        // --- HIER IST DIE KORREKTUR ---
        // Der Methodenaufruf muss in einer Zeile mit den richtigen Parametern erfolgen.
        // Deine neue Methode im UserService erwartet den ganzen `currentUser`.
        List<UserDTO> nearbyUsers = userService.getNearbyUsers(
                latitude,
                longitude,
                radius,
                currentUser
        );

        return ResponseEntity.ok(nearbyUsers);
    }

    /**
     * Retrieves the profile information for the currently authenticated user.
     *
     * @param currentUser The user object, automatically injected by Spring Security from the auth token.
     * @return A ResponseEntity containing the UserDTO with the user's ID, username, and signed profile image URL.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUserProfile(@AuthenticationPrincipal User currentUser) {
        // Wir übergeben den User direkt an den Service, der ihn in ein DTO umwandelt.
        UserDTO userProfile = userService.getUserProfile(currentUser);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal User currentUser,
            @RequestBody @Valid FcmTokenUpdateRequestDTO request) {
        userService.updateFcmToken(currentUser, request.token());
        return ResponseEntity.ok().build();
    }
}