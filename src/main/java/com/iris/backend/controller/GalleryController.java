package com.iris.backend.controller;

import com.iris.backend.dto.HistoricalSearchRequestDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.service.PlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/galleries")
public class GalleryController {

    private final PlaceService placeService;

    /**
     * Constructs a new instance of GalleryController with the specified PlaceService.
     *
     * @param placeService the service used to manage and provide place-related data
     */
    public GalleryController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /**
     * Retrieves a list of historical galleries based on the provided search request and radius.
     *
     * This method takes in a search request containing a list of historical points
     * and searches for galleries that match the historical context within the specified
     * radius. Results are returned as a list of PlaceDTO objects.
     *
     * @param searchRequest the search request containing historical points for the query
     * @param radius the radius in meters within which to search for historical galleries,
     *               defaults to 25000 if not specified
     * @return a ResponseEntity containing a list of PlaceDTO objects representing the
     *         historical galleries found, or an empty list if no matches are found
     */
    @PostMapping("/historical-search")
    public ResponseEntity<List<PlaceDTO>> getHistoricalGalleries(
            @RequestBody HistoricalSearchRequestDTO searchRequest,
            @RequestParam(defaultValue = "25000") double radius) {

        List<PlaceDTO> galleries = placeService.findHistoricalGalleriesBatch(searchRequest.history(), radius);
        return ResponseEntity.ok(galleries);
    }
}