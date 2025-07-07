package com.chaptime.backend.dto;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;

public record HistoricalPointDTO(
        double latitude,
        double longitude,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime timestamp
) {}