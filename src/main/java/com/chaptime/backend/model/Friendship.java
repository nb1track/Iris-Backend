package com.chaptime.backend.model;

import com.chaptime.backend.model.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    @Column(nullable = false)
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