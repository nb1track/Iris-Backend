package com.iris.backend.service;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.User;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.PhotoRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CustomPlaceService {

    private final CustomPlaceRepository customPlaceRepository;
    private final PhotoRepository photoRepository; // Abhängigkeit hinzugefügt
    private final GcsStorageService gcsStorageService; // Abhängigkeit hinzugefügt
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${gcs.bucket.profile-images.name}") // Lädt den Bucket-Namen aus application.properties
    private String profileImagesBucketName;

    // Konstruktor mit allen benötigten Abhängigkeiten
    public CustomPlaceService(
            CustomPlaceRepository customPlaceRepository,
            PhotoRepository photoRepository,
            GcsStorageService gcsStorageService
    ) {
        this.customPlaceRepository = customPlaceRepository;
        this.photoRepository = photoRepository;
        this.gcsStorageService = gcsStorageService;
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
        newPlace.setChallengesActivated(request.challengesActivated() != null && request.challengesActivated());

        if (request.isLive() || request.scheduledLiveAt() == null) {
            newPlace.setLive(true);
            newPlace.setScheduledLiveAt(null); // Sicherstellen, dass es null ist, wenn isLive true ist
        } else {
            newPlace.setLive(false);
            newPlace.setScheduledLiveAt(request.scheduledLiveAt());
        }

        return customPlaceRepository.save(newPlace);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getParticipants(UUID placeId, User currentUser) {
        // 1. Finde den Custom Place
        CustomPlace place = customPlaceRepository.findById(placeId)
                .orElseThrow(() -> new RuntimeException("Custom Place not found with ID: " + placeId));

        // 2. Sicherheitscheck: Ist der anfragende User der Ersteller des Spots?
        if (!place.getCreator().getId().equals(currentUser.getId())) {
            throw new SecurityException("User is not authorized to view participants for this place.");
        }

        // 3. Finde alle einzigartigen Uploader für diesen Place
        List<User> participants = photoRepository.findDistinctUploadersByCustomPlace(place);

        // 4. Wandle die User-Objekte in DTOs um, inklusive der signierten Profilbild-URL
        return participants.stream()
                .map(user -> {
                    String signedProfileUrl = null;
                    String objectName = user.getProfileImageUrl();

                    // Generiere die URL, falls ein Profilbild vorhanden ist
                    if (objectName != null && !objectName.isBlank()) {
                        signedProfileUrl = gcsStorageService.generateSignedUrl(
                                profileImagesBucketName,
                                objectName,
                                15,
                                TimeUnit.MINUTES
                        );
                    }

                    // Erstelle das DTO mit allen 3 benötigten Argumenten
                    return new UserDTO(user.getId(), user.getUsername(), signedProfileUrl);
                })
                .collect(Collectors.toList());
    }
}