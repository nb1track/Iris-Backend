package com.iris.backend.controller;

import com.iris.backend.dto.FeedPlaceDTO;
import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.service.FeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @PostMapping("/historical")
    public ResponseEntity<List<FeedPlaceDTO>> getHistoricalFeed(
            @RequestBody HistoricalSearchRequestDTO searchRequest) {

        List<FeedPlaceDTO> feed = feedService.generateHistoricalFeed(searchRequest.history());
        return ResponseEntity.ok(feed);
    }
}