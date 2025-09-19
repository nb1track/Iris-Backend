package com.iris.backend.service;

import com.iris.backend.model.Photo;
import com.iris.backend.model.PhotoLike;
import com.iris.backend.model.PhotoLikeId;
import com.iris.backend.model.User;
import com.iris.backend.repository.PhotoLikeRepository;
import com.iris.backend.repository.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PhotoLikeService {

    private final PhotoLikeRepository photoLikeRepository;
    private final PhotoRepository photoRepository;

    public PhotoLikeService(PhotoLikeRepository photoLikeRepository, PhotoRepository photoRepository) {
        this.photoLikeRepository = photoLikeRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional
    public void toggleLike(UUID photoId, User currentUser) {
        // Sicherstellen, dass das Foto existiert
        if (!photoRepository.existsById(photoId)) {
            throw new RuntimeException("Photo not found with ID: " + photoId);
        }

        PhotoLikeId likeId = new PhotoLikeId(currentUser.getId(), photoId);
        Optional<PhotoLike> existingLike = photoLikeRepository.findById(likeId);

        if (existingLike.isPresent()) {
            // Like existiert -> entfernen (unlike)
            photoLikeRepository.delete(existingLike.get());
        } else {
            // Like existiert nicht -> hinzufügen (like)
            PhotoLike newLike = new PhotoLike();
            Photo photoReference = photoRepository.getReferenceById(photoId); // Holen einer Referenz ist effizienter
            newLike.setId(likeId);
            newLike.setUser(currentUser);
            newLike.setPhoto(photoReference);
            photoLikeRepository.save(newLike);
        }
    }
}