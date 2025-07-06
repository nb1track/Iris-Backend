package com.chaptime.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;

/**
 * Represents a geographical or physical location that can be associated with various entities in the system.
 *
 * The Place entity models a specific location, storing details such as:
 * - A unique identifier for the place.
 * - A unique identifier associated with Google Places API.
 * - The name of the place.
 * - The address of the place, if available.
 * - The geographical location using latitude and longitude in Point format.
 * - The timestamp indicating when this place entry was created.
 *
 * The creation timestamp is automatically set during the persistence process.
 *
 * This entity is used in association with other entities, such as photos, that reference a specific place.
 */
@Entity
@Table(name = "places")
@Getter
@Setter
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_place_id", nullable = false, unique = true)
    private String googlePlaceId;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Column(name = "location", columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

}