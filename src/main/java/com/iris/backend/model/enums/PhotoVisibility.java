package com.iris.backend.model.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PhotoVisibility {
    // 1. "Nur für den Spot" (Frontend: toSpots -> TOSPOTS)
    // Wir behalten intern den Namen PUBLIC, damit wir nicht den ganzen Code refactoren müssen,
    // der "PUBLIC" als "sichtbar am Ort" versteht.
    @JsonProperty("TOSPOTS")
    PUBLIC,

    // 2. "Nur für Freunde" (Frontend: toFriends -> TOFRIENDS)
    @JsonProperty("TOFRIENDS")
    FRIENDS,

    // 3. "Für Freunde UND Spot" (Frontend: public -> PUBLIC)
    // Das ist der neue Hybrid-Modus.
    @JsonProperty("PUBLIC")
    VISIBLE_TO_ALL,
}