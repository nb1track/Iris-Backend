package com.chaptime.backend.dto;

import java.util.UUID;

public record PhotoResponseDTO(
        UUID photoId,
        String storageUrl,
        String uploaderUsername
) {}