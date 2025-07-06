package com.chaptime.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a user entity in the system.
 *
 * The User entity models a user with unique identifiers and associated metadata.
 * It includes details such as:
 * - A unique UUID as the primary identifier.
 * - A Firebase UID for authentication purposes.
 * - A unique username, restricted to a maximum length of 50 characters.
 * - A unique and validated email address.
 * - The timestamp indicating when the user was created.
 * - The user's last known geographical location, represented in Point format.
 * - The timestamp of the last update to the user's location.
 *
 * The creation timestamp is automatically set before persisting a new User entity.
 * The generated id value is automatically created and managed by the persistence layer.
 */
@Entity
@Table(name = "users") // Wichtig: Name der Tabelle in der DB
@Getter // Lombok-Annotation für automatische Getter
@Setter // Lombok-Annotation für automatische Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Hibernate braucht einen leeren Konstruktor
    public User() {
    }

    @PrePersist
    public void onPrePersist() {
        // Setzt das Erstellungsdatum automatisch vor dem ersten Speichern
        createdAt = OffsetDateTime.now();
    }

    @Column(name = "last_location", columnDefinition = "geography(Point, 4326)")
    private Point lastLocation;

    @Column(name = "last_location_updated_at")
    private OffsetDateTime lastLocationUpdatedAt;
}