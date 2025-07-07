package com.chaptime.backend.service;

import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    /**
     * Constructs a new instance of PlaceService.
     *
     * @param placeRepository the repository to manage Place objects for retrieving
     *                        and storing place-related data.
     */
    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * Retrieves a list of public galleries within a specified radius of a given location and time window.
     *
     * The method filters places based on their geographical proximity to the provided latitude and longitude,
     * checks if they have active public photos within the specified time window, and converts the results into
     * a list of PlaceDTO objects.
     *
     * @param latitude the latitude of the center point for the search area
     * @param longitude the longitude of the center point for the search area
     * @param radius the radius (in meters) around the specified location to search for public galleries
     * @param timestamp an optional timestamp used to define the time window for filtering active public photos
     * @return a list of PlaceDTO objects representing the public galleries that meet the specified criteria
     */
    public List<PlaceDTO> getPublicGalleries(double latitude, double longitude, double radius, Optional<OffsetDateTime> timestamp) {
        List<Place> places;

            places = placeRepository.findPlacesWithActivePublicPhotosInTimeWindow(latitude, longitude, radius, timestamp.get());


        return places.stream()
                .map(place -> new PlaceDTO(
                        place.getId(),
                        place.getGooglePlaceId(),
                        place.getName(),
                        place.getAddress()
                ))
                .collect(Collectors.toList());
    }
}