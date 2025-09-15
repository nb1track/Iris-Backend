package com.iris.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenUpdateRequestDTO(@NotBlank String token) {}