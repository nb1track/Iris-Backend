package com.iris.backend.dto;

import java.util.List;

public record PlaceDTO(
        Long id,
        String googlePlaceId,
        String name,
        String address,
        List<PhotoResponseDTO> photos,
        Integer radiusMeters,
        Integer importance
) {}