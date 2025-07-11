package com.chaptime.backend.dto;

import java.util.UUID;

public record PendingRequestDTO(
        UUID friendshipId, // Die ID der Freundschaftsanfrage
        String requesterUsername // Der Name des Anfragenden
) {}