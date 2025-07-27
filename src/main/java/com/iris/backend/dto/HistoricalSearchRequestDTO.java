package com.iris.backend.dto;

import java.util.List;

public record HistoricalSearchRequestDTO(
        List<HistoricalPointDTO> history
) {}
