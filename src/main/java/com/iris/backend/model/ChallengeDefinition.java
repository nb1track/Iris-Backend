package com.iris.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "challenge_definitions")
@Getter
@Setter
public class ChallengeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "icon_key", nullable = false, length = 100)
    private String iconKey;
}