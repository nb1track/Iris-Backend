package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blocked_numbers")
@Getter
@Setter
public class BlockedNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "reason")
    private String reason;

    @Column(name = "blocked_at", nullable = false)
    private OffsetDateTime blockedAt;

    @PrePersist
    protected void onBlock() {
        blockedAt = OffsetDateTime.now();
    }
}