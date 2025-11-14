package com.iris.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ChallengeParticipantId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "place_challenge_id")
    private UUID placeChallengeId;

    public ChallengeParticipantId(UUID userId, UUID placeChallengeId) {
        this.userId = userId;
        this.placeChallengeId = placeChallengeId;
    }
}