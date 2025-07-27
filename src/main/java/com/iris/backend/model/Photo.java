package com.iris.backend.model;

import com.iris.backend.model.enums.PhotoVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a Photo entity in the system.
 *
 * The Photo entity models a photo uploaded by a user to a specific location or place.
 * It stores metadata about the photo, such as its visibility, upload time, expiration,
 * and storage details.
 *
 * Each Photo object contains the following information:
 * - A unique identifier for the photo.
 * - The user who uploaded the photo.
 * - An optional associated place.
 * - Geographical location of where the photo was taken or uploaded.
 * - Visibility level, which determines who can view the photo.
 * - URL of the storage location where the photo file is saved.
 * - Timestamp for when the photo was uploaded.
 * - Timestamp for when the photo expires and becomes inaccessible.
 *
 * The upload timestamp is automatically set upon creating a new Photo instance in the database.
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Enumerated(EnumType.STRING) // <-- Wichtig: STRING
    @Column(name = "visibility", nullable = false) // <-- columnDefinition entfernt
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