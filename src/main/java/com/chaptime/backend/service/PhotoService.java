package com.chaptime.backend.service;

import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.Place;
import com.chaptime.backend.model.User;
import com.chaptime.backend.model.enums.PhotoVisibility;
import com.chaptime.backend.repository.PhotoRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final FriendshipService friendshipService;
    private final GoogleApiService googleApiService;
    // private final GoogleCloudStorageService storageService; // Für später

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public PhotoService(PhotoRepository photoRepository, FriendshipService friendshipService, GoogleApiService googleApiService) {
        this.photoRepository = photoRepository;
        this.friendshipService = friendshipService;
        this.googleApiService = googleApiService;
    }

    @Transactional
    // Parameter 'googlePlaceId' entfernt, da er nicht mehr benötigt wird
    public UUID createPhoto(MultipartFile file, double latitude, double longitude, PhotoVisibility visibility, User user) {
        // 1. Bild in Google Cloud Storage hochladen (Platzhalter)
        String fileUrl = "https://placeholder.url/for/now/" + file.getOriginalFilename();

        // 2. Geo-Punkt aus Koordinaten erstellen
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        // 3. Finde oder erstelle einen Ort für die Koordinaten über den GoogleApiService
        Optional<Place> placeOptional = googleApiService.findOrCreatePlaceForCoordinates(latitude, longitude);

        // 4. Photo-Objekt erstellen und befüllen
        Photo newPhoto = new Photo();
        placeOptional.ifPresent(newPhoto::setPlace); // Setze den Ort, falls einer gefunden wurde
        newPhoto.setUploader(user);
        newPhoto.setLocation(location);
        newPhoto.setVisibility(visibility);
        newPhoto.setStorageUrl(fileUrl);

        // 5. Ablaufdatum berechnen
        OffsetDateTime now = OffsetDateTime.now();
        newPhoto.setUploadedAt(now);
        if (visibility == PhotoVisibility.PUBLIC) {
            newPhoto.setExpiresAt(now.plusHours(48));
        } else { // Annahme: FRIENDS
            newPhoto.setExpiresAt(now.plusDays(7));
        }

        // 6. Photo-Objekt in der Datenbank speichern
        Photo savedPhoto = photoRepository.save(newPhoto);

        // 7. Die ID des gespeicherten Fotos zurückgeben
        return savedPhoto.getId();
    }

    public List<PhotoResponseDTO> findDiscoverablePhotos(double latitude, double longitude, double radiusInMeters) {
        List<Photo> photos = photoRepository.findPublicPhotosWithinRadius(latitude, longitude, radiusInMeters);
        return photos.stream()
                .map(photo -> new PhotoResponseDTO(
                        photo.getId(),
                        photo.getStorageUrl(),
                        photo.getUploader().getUsername()
                ))
                .collect(Collectors.toList());
    }

    public List<PhotoResponseDTO> getFriendsFeed(UUID userId) {
        List<User> friends = friendshipService.getFriendsAsEntities(userId);
        if (friends.isEmpty()) {
            return List.of();
        }
        List<Photo> photos = photoRepository
                .findAllByUploaderInAndVisibilityAndExpiresAtAfterOrderByUploadedAtDesc(
                        friends,
                        PhotoVisibility.FRIENDS,
                        OffsetDateTime.now()
                );
        return photos.stream()
                .map(photo -> new PhotoResponseDTO(
                        photo.getId(),
                        photo.getStorageUrl(),
                        photo.getUploader().getUsername()
                ))
                .collect(Collectors.toList());
    }
}