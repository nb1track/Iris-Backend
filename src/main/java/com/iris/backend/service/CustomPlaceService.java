package com.iris.backend.service;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.repository.CustomPlaceRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Annahme: Es gibt ein DTO, um einen Place zu erstellen.
// import com.iris.backend.dto.CreateCustomPlaceRequestDTO;

@Service
public class CustomPlaceService {

    private final CustomPlaceRepository customPlaceRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public CustomPlaceService(CustomPlaceRepository customPlaceRepository) {
        this.customPlaceRepository = customPlaceRepository;
    }

    @Transactional
    public CustomPlace createCustomPlace(CreateCustomPlaceRequestDTO request, User creator) {
        // Sicherheits-Check, ob der User wirklich vor Ort ist.
        Point requestLocation = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));
        if (creator.getLastLocation() == null || creator.getLastLocation().distance(requestLocation) > 200) { // 200m Toleranz
            throw new IllegalStateException("User must be near the location to create a custom place.");
        }

        CustomPlace newPlace = new CustomPlace();
        newPlace.setCreator(creator);
        newPlace.setName(request.name());
        newPlace.setLocation(geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude())));
        newPlace.setRadiusMeters(request.radiusMeters());
        newPlace.setAccessType(request.accessType());
        newPlace.setAccessKey(request.accessKey());
        newPlace.setTrending(request.isTrending());
        newPlace.setLive(request.isLive());
        newPlace.setScheduledLiveAt(request.scheduledLiveAt());
        newPlace.setExpiresAt(request.expiresAt());

        if(request.challengesActivated() != null) {
            newPlace.setChallengesActivated(request.challengesActivated());
        }

        return customPlaceRepository.save(newPlace);
    }
}