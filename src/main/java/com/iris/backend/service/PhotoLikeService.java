package com.iris.backend.service;

import com.iris.backend.model.Photo;
import com.iris.backend.model.PhotoLike;
import com.iris.backend.model.PhotoLikeId;
import com.iris.backend.model.User;
import com.iris.backend.repository.FriendshipRepository;
import com.iris.backend.repository.PhotoLikeRepository;
import com.iris.backend.repository.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PhotoLikeService {

    private final PhotoLikeRepository photoLikeRepository;
    private final PhotoRepository photoRepository;
    private final FriendshipRepository friendshipRepository; // NEU

    public PhotoLikeService(PhotoLikeRepository photoLikeRepository,
                            PhotoRepository photoRepository,
                            FriendshipRepository friendshipRepository) {
        this.photoLikeRepository = photoLikeRepository;
        this.photoRepository = photoRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional
    public void toggleLike(UUID photoId, User currentUser) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));

        PhotoLikeId likeId = new PhotoLikeId(currentUser.getId(), photoId);
        Optional<PhotoLike> existingLike = photoLikeRepository.findById(likeId);

        if (existingLike.isPresent()) {
            // Like existiert -> entfernen (unlike)
            photoLikeRepository.delete(existingLike.get());
        } else {
            // Like existiert nicht -> hinzufügen (like)
            PhotoLike newLike = new PhotoLike();
            newLike.setId(likeId);
            newLike.setUser(currentUser);
            newLike.setPhoto(photo);
            photoLikeRepository.save(newLike);

            User uploader = photo.getUploader();
            if (!uploader.getId().equals(currentUser.getId())) {
                friendshipRepository.findFriendshipBetweenUsers(currentUser, uploader).ifPresent(friendship -> {
                    friendship.setInteractionScore(friendship.getInteractionScore() + 1);
                    friendship.setLastInteractedAt(OffsetDateTime.now());
                    friendshipRepository.save(friendship);
                });
            }
        }
    }
}