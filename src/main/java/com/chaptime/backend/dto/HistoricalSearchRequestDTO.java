package com.chaptime.backend.dto;

import java.util.List;

public record HistoricalSearchRequestDTO(
        List<HistoricalPointDTO> history,
        Double radius
) {}
