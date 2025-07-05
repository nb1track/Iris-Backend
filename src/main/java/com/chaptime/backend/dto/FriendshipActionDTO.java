package com.chaptime.backend.dto;

import java.util.UUID;

// Dieses DTO kann sowohl zum Annehmen als auch zum Ablehnen verwendet werden
public record FriendshipActionDTO(UUID friendshipId) {}