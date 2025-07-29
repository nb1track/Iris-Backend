package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Represents a User entity that is managed within the application's database.
 *
 * The User entity is used to store and manage data associated with a user, including:
 * - A unique identifier for the user.
 * - A Firebase UID used for integration with Firebase services.
 * - A unique username for the user account.
 * - The email address associated with the user.
 * - The timestamp indicating when the user account was created.
 * - The user's last known geographic location and the timestamp when it was updated.
 *
 * This class implements the {@link UserDetails}
 * interface for integration with Spring Security, allowing it to provide default authentication
 * and authorization capabilities. As a `UserDetails` implementation:
 * - Each user is assigned a default role of "ROLE_USER".
 * - Password functionality, account expiration, and locking features are not utilized.
 *
 * The creation timestamp is automatically set before the entity is persisted to the database.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
// --- HIER DIE ÄNDERUNG: UserDetails implementieren ---
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_location", columnDefinition = "geography(Point, 4326)")
    private Point lastLocation;

    @Column(name = "last_location_updated_at")
    private OffsetDateTime lastLocationUpdatedAt;

    public User() {
    }

    @PrePersist
    public void onPrePersist() {
        createdAt = OffsetDateTime.now();
    }

    // --- NEUE METHODEN VON UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Wir geben jedem Benutzer eine einfache Rolle.
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        // Wir benutzen keine Passwörter in unserer DB, also null.
        return null;
    }

    // Für die folgenden Methoden geben wir einfach 'true' zurück,
    // da wir die Account-Sperrung etc. nicht nutzen.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}