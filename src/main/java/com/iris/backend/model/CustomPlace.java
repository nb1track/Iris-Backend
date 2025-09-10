package com.iris.backend.model;

import com.iris.backend.model.enums.PlaceAccessType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "custom_places")
@Getter
@Setter
public class CustomPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private PlaceAccessType accessType;

    @Column(name = "access_key")
    private String accessKey; // For password or QR code content

    @Column(name = "is_trending")
    private boolean isTrending = false;

    @Column(name = "is_live")
    private boolean isLive = false;

    @Column(name = "scheduled_live_at")
    private OffsetDateTime scheduledLiveAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "challenges_activated")
    private boolean challengesActivated = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}