package com.iris.backend.service;

import com.iris.backend.model.Photo;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FcmService {

    private static final Logger logger = LoggerFactory.getLogger(FcmService.class);

    @Async // Wichtig: Führt die Methode in einem separaten Thread aus
    public void sendNewPhotoNotification(List<String> tokens, Photo photo) {
        if (tokens.isEmpty()) {
            return;
        }

        // Das ist eine "Data Message" (Silent Push). Sie hat KEIN "notification"-Feld.
        MulticastMessage message = MulticastMessage.builder()
                .putAllData(Map.of(
                        "type", "NEW_FRIEND_PHOTO",
                        "photoId", photo.getId().toString(),
                        "storageUrl", photo.getStorageUrl(),
                        "uploaderUsername", photo.getUploader().getUsername()
                ))
                .addAllTokens(tokens)
                .build();
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            logger.info("Successfully sent FCM message to {} devices.", response.getSuccessCount());
            if (response.getFailureCount() > 0) {
                // Hier könntest du Logik hinzufügen, um ungültige Tokens aus der DB zu entfernen
                logger.warn("Failed to send FCM message to {} devices.", response.getFailureCount());
            }
        } catch (FirebaseMessagingException e) {
            logger.error("Error sending FCM message", e);
        }
    }
}