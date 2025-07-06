package com.chaptime.backend.controller;

import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.service.PlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * Retrieves a list of public galleries based on the specified geographical coordinates
     * and search radius. The results are filtered to include only galleries with active,
     * publicly available photos.
     *
     * @param latitude the latitude of the location to search around
     * @param longitude the longitude of the location to search around
     * @param radius the search radius in meters (default is 25000 meters if not specified)
     * @return a ResponseEntity containing a list of PlaceDTO objects representing the
     *         public galleries within the specified radius
     */
    @GetMapping
    public ResponseEntity<List<PlaceDTO>> getPublicGalleries(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "25000") double radius // 25km als Standard-Suchradius
    ) {
        List<PlaceDTO> galleries = placeService.getPublicGalleries(latitude, longitude, radius);
        return ResponseEntity.ok(galleries);
    }
}