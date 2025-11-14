package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "custom_place_challenges")
@Getter
@Setter
public class CustomPlaceChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_place_id", nullable = false)
    private CustomPlace customPlace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_definition_id", nullable = false)
    private ChallengeDefinition challengeDefinition;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}