package com.chaptime.backend.dto;

import java.util.UUID;

public record FriendRequestDTO(
        UUID requesterId, // Wird später durch den angemeldeten User ersetzt
        UUID addresseeId  // Die ID des Users, dem die Anfrage gesendet wird
) {}