package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;

@Entity
@Table(name = "google_places")
@Getter
@Setter
public class GooglePlace {

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

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    @Column(name = "importance", nullable = false)
    private Integer importance = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}