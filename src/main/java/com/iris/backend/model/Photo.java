package com.iris.backend.model;

import com.iris.backend.model.enums.PhotoVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Getter
@Setter
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    // NEU: Verknüpfung zu GooglePlace (vorher Place)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "google_place_id")
    private GooglePlace googlePlace;

    // NEU: Verknüpfung zu CustomPlace
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_place_id")
    private CustomPlace customPlace;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private PhotoVisibility visibility;

    @Column(name = "storage_url", nullable = false, length = 1024)
    private String storageUrl;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onUpload() {
        uploadedAt = OffsetDateTime.now();
    }
}