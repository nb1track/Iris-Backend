package com.chaptime.backend.controller;

import com.chaptime.backend.dto.LocationUpdateRequestDTO;
import com.chaptime.backend.model.User;
import com.chaptime.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.chaptime.backend.dto.UserDataExportDTO;
import com.chaptime.backend.dto.SignUpRequestDTO;
import com.chaptime.backend.dto.UserDTO;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

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
    public ResponseEntity<UserDTO> registerUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SignUpRequestDTO signUpRequest) {

        // Manuelle Token-Verifizierung nur für diesen Endpunkt
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String idToken = authHeader.substring(7);

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

            User newUser = userService.registerNewUser(decodedToken, signUpRequest.username());

            UserDTO userDTO = new UserDTO(newUser.getId(), newUser.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);

        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // 409 Conflict if user exists
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
}