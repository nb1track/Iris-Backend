package com.chaptime.backend.service;

import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

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
     * Retrieves a list of public galleries within a specified radius of a given geographical location.
     * The method filters places that have active public photos and maps them to a list of PlaceDTO objects.
     *
     * @param latitude the latitude of the central point to search for public galleries
     * @param longitude the longitude of the central point to search for public galleries
     * @param radius the radius (in meters) around the given location within which to search for public galleries
     * @return a list of PlaceDTO objects representing public galleries within the specified radius
     */
    public List<PlaceDTO> getPublicGalleries(double latitude, double longitude, double radius) {
        List<Place> places = placeRepository.findPlacesWithActivePublicPhotos(latitude, longitude, radius);

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