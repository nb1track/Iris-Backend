package com.iris.backend.service;

import com.iris.backend.model.Photo;
import com.iris.backend.model.User;
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

    @Async
    public void sendNewPhotoNotification(List<String> tokens, Photo photo) {
        if (tokens.isEmpty()) {
            return;
        }

        // MulticastMessage ist weiterhin korrekt, aber wir nutzen die neue Send-Methode
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
            // WICHTIG: sendEachForMulticast statt sendMulticast verwenden!
            // sendEachForMulticast nutzt die HTTP v1 API.
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            logger.info("Successfully sent FCM message to {} devices.", response.getSuccessCount());

            if (response.getFailureCount() > 0) {
                logger.warn("Failed to send FCM message to {} devices.", response.getFailureCount());
                // Optional: Fehleranalyse für einzelne Tokens
                response.getResponses().forEach(r -> {
                   if (!r.isSuccessful()) logger.error("Failure: {}", r.getException().getMessage());
                });
            }
        } catch (FirebaseMessagingException e) {
            logger.error("Error sending FCM message via HTTP v1", e);
        }
    }

    /**
     * Sendet eine "Bitte-sende-mir-deinen-Standort"-Anfrage an eine Liste von Freunden.
     */
    @Async
    public void sendLocationRefreshRequest(List<String> friendTokens, String requesterFcmToken) {
        if (friendTokens.isEmpty() || requesterFcmToken == null || requesterFcmToken.isBlank()) {
            logger.warn("sendLocationRefreshRequest abgebrochen: Keine Tokens oder kein Anfrager-Token.");
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .putAllData(Map.of(
                        "type", "REQUEST_LOCATION",
                        "requesterFcmToken", requesterFcmToken
                ))
                .addAllTokens(friendTokens)
                .build();

        try {
            // Auch hier: Umstieg auf sendEachForMulticast
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            logger.info("Standortanfrage an {} Geräte gesendet.", response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            logger.error("Fehler beim Senden der Standortanfrage", e);
        }
    }

    @Async
    public void sendLocationRefreshResponse(String targetToken, User friend, double latitude, double longitude, String profileImageUrl) {
        Map<String, String> data = new java.util.HashMap<>();
        data.put("type", "FRIEND_LOCATION_UPDATE");
        data.put("friendId", friend.getId().toString());
        data.put("friendUsername", friend.getUsername());
        data.put("latitude", String.valueOf(latitude));
        data.put("longitude", String.valueOf(longitude));

        // NEU: Bild mitsenden
        if (profileImageUrl != null) {
            data.put("friendProfileImageUrl", profileImageUrl);
        }

        Message message = Message.builder()
                .putAllData(data)
                .setToken(targetToken)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            logger.info("Standort-Antwort von {} an {} gesendet: {}", friend.getUsername(), targetToken, response);
        } catch (FirebaseMessagingException e) {
            logger.error("Fehler beim Senden der Standort-Antwort", e);
        }
    }

    /**
     * Sends a "ping" notification to a specific target user.
     */
    public void sendPingNotification(String targetToken, User sender, String senderProfileUrl) {
        if (targetToken == null || targetToken.isEmpty()) return;

        Map<String, String> data = new java.util.HashMap<>();
        data.put("type", "FRIEND_PING");
        data.put("senderId", sender.getId().toString());
        data.put("senderUsername", sender.getUsername());
        data.put("timestamp", java.time.OffsetDateTime.now().toString());

        if (senderProfileUrl != null) {
            data.put("senderProfileImageUrl", senderProfileUrl);
        }

        // OPTIMIERUNG: Da es nur EIN Token ist, nutzen wir Message statt MulticastMessage.
        // Das ist effizienter und nutzt direkt die Einzel-API.
        Message message = Message.builder()
                .setToken(targetToken) // setToken statt addToken
                .putAllData(data)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message); // send() statt sendMulticast()
            logger.info("Sent PING from {} to token ending in ...{}", sender.getUsername(), targetToken.substring(Math.max(0, targetToken.length() - 6)));
        } catch (FirebaseMessagingException e) {
            logger.error("Error sending Ping FCM", e);
        }
    }
}