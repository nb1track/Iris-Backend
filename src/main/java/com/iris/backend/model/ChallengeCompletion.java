package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "challenge_completions")
@Getter
@Setter
public class ChallengeCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_challenge_id", nullable = false)
    private CustomPlaceChallenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    private Photo photo; // Das "Beweis"-Foto

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onComplete() {
        completedAt = OffsetDateTime.now();
    }
}