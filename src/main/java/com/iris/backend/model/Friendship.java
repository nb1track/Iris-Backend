package com.iris.backend.model;

import com.iris.backend.model.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a friendship between two users in the system.
 *
 * The Friendship entity models the relationship and status between two users,
 * including who initiated the action and when the friendship was created.
 * It ensures that each friendship relationship is unique.
 *
 * An instance of Friendship contains information about:
 * - The unique identifier for the friendship.
 * - The two users involved in the friendship relationship.
 * - The current status of the friendship (e.g., pending or accepted).
 * - The user who initiated the action (e.g., sent the request).
 * - The timestamp when the friendship was created.
 *
 * This entity enforces uniqueness of friendships such that a relationship between
 * the two same users can only exist once.
 */
@Entity
@Table(name = "friendships",
        // Stellt sicher, dass eine Freundschaft nur einmal existiert
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"}))
@Getter
@Setter
public class Friendship {

    // Wir brauchen einen eigenen Primärschlüssel, da dieser aus zwei Spalten besteht
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_one_id", nullable = false)
    private User userOne;

    @ManyToOne
    @JoinColumn(name = "user_two_id", nullable = false)
    private User userTwo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "friendship_status")
    private FriendshipStatus status;

    // Der User, der die Aktion ausgeführt hat (z.B. die Anfrage gesendet)
    @ManyToOne
    @JoinColumn(name = "action_user_id", nullable = false)
    private User actionUser;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}