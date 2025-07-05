package com.chaptime.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.UUID;

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