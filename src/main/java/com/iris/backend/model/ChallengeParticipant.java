package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "challenge_participants")
@Getter
@Setter
public class ChallengeParticipant {

    @EmbeddedId
    private ChallengeParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("placeChallengeId")
    private CustomPlaceChallenge challenge;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    protected void onJoin() {
        joinedAt = OffsetDateTime.now();
    }
}