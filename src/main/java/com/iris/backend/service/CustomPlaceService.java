package com.iris.backend.service;

import com.iris.backend.dto.CreateCustomPlaceRequestDTO;
import com.iris.backend.dto.ParticipantDTO;
import com.iris.backend.dto.UserDTO;
import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.Friendship;
import com.iris.backend.model.User;
import com.iris.backend.model.enums.FriendshipStatus;
import com.iris.backend.repository.CustomPlaceRepository;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.PhotoRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CustomPlaceService {

    private final CustomPlaceRepository customPlaceRepository;
    private final PhotoRepository photoRepository; // Abhängigkeit hinzugefügt
    private final FriendshipRepository friendshipRepository;
    private final GcsStorageService gcsStorageService; // Abhängigkeit hinzugefügt
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${gcs.bucket.profile-images.name}") // Lädt den Bucket-Namen aus application.properties
    private String profileImagesBucketName;

    // Konstruktor mit allen benötigten Abhängigkeiten
    public CustomPlaceService(
            CustomPlaceRepository customPlaceRepository,
            PhotoRepository photoRepository,
            FriendshipRepository friendshipRepository,
            GcsStorageService gcsStorageService
    ) {
        this.customPlaceRepository = customPlaceRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
        this.gcsStorageService = gcsStorageService;
    }

    @Transactional
    public CustomPlace createCustomPlace(CreateCustomPlaceRequestDTO request, MultipartFile coverImage, User creator) throws IOException {
        // Sicherheits-Check, ob der User wirklich vor Ort ist.
        Point requestLocation = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));
        if (creator.getLastLocation() == null || creator.getLastLocation().distance(requestLocation) > 200) { // 200m Toleranz
            throw new IllegalStateException("User must be near the location to create a custom place.");
        }

        String coverImageObjectName = gcsStorageService.uploadPhoto(coverImage);

        CustomPlace newPlace = new CustomPlace();
        newPlace.setCreator(creator);
        newPlace.setName(request.name());
        newPlace.setLocation(geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude())));
        newPlace.setRadiusMeters(request.radiusMeters());
        newPlace.setAccessType(request.accessType());
        newPlace.setAccessKey(request.accessKey());
        newPlace.setTrending(request.isTrending());
        newPlace.setCoverImageUrl(coverImageObjectName);
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
    public List<ParticipantDTO> getParticipants(UUID placeId, User currentUser) { // Rückgabetyp geändert
        // 1. Finde den Custom Place
        CustomPlace place = customPlaceRepository.findById(placeId)
                .orElseThrow(() -> new RuntimeException("Custom Place not found with ID: " + placeId));

        // 2. Sicherheitscheck
        if (!place.getCreator().getId().equals(currentUser.getId())) {
            throw new SecurityException("User is not authorized to view participants for this place.");
        }

        // 3. Finde Teilnehmer
        List<User> participants = photoRepository.findDistinctUploadersByCustomPlace(place);

        // 4. Hole Freundschaften
        List<Friendship> friendships = friendshipRepository.findByUserOneAndStatusOrUserTwoAndStatus(
                currentUser, FriendshipStatus.ACCEPTED,
                currentUser, FriendshipStatus.ACCEPTED
        );

        Set<UUID> friendIds = friendships.stream()
                .map(f -> f.getUserOne().getId().equals(currentUser.getId()) ? f.getUserTwo().getId() : f.getUserOne().getId())
                .collect(Collectors.toSet());

        // 5. Wandle in ParticipantDTO um
        return participants.stream()
                .map(user -> {
                    String signedProfileUrl = null;
                    String objectName = user.getProfileImageUrl();

                    if (objectName != null && !objectName.isBlank()) {
                        signedProfileUrl = gcsStorageService.generateSignedUrl(
                                profileImagesBucketName,
                                objectName,
                                15,
                                TimeUnit.MINUTES
                        );
                    }

                    boolean isFriend = friendIds.contains(user.getId());
                    if (user.getId().equals(currentUser.getId())) {
                        isFriend = false;
                    }

                    // Nutze das neue ParticipantDTO
                    return new ParticipantDTO(user.getId(), user.getUsername(), signedProfileUrl, isFriend);
                })
                .collect(Collectors.toList());
    }
}