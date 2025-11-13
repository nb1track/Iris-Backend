package com.iris.backend.dto;

import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import java.util.List;

/**
 * Repräsentiert einen Spot (Google oder Iris), an dem sich einer
 * oder mehrere Freunde des aktuellen Benutzers befinden.
 */
public record FriendAtSpotDTO(
        GalleryFeedItemDTO spotInfo,
        List<UserDTO> friendsAtSpot
) {}