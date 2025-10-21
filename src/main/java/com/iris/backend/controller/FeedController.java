package com.iris.backend.controller;

import com.iris.backend.dto.feed.GalleryFeedItemDTO;
import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.service.HistoricalFeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final HistoricalFeedService historicalFeedService;

    public FeedController(HistoricalFeedService historicalFeedService) {
        this.historicalFeedService = historicalFeedService;
    }

    @PostMapping("/historical")
    public ResponseEntity<List<GalleryFeedItemDTO>> getHistoricalFeed(
                                                                       @RequestBody HistoricalSearchRequestDTO searchRequest) {

        List<GalleryFeedItemDTO> feed = historicalFeedService.generateHistoricalFeed(searchRequest.history());
        return ResponseEntity.ok(feed);
    }
}