package com.iris.backend.service;

import com.iris.backend.model.Photo;
import com.google.firebase.messaging.*;
import com.iris.backend.model.User;
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

    /**
     * NEU: Sendet eine "Bitte-sende-mir-deinen-Standort"-Anfrage an eine Liste von Freunden.
     * @param friendTokens Die FCM-Tokens der Freunde (B, C, D...)
     * @param requesterFcmToken Das FCM-Token des Anfragers (User A)
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
                        "requesterFcmToken", requesterFcmToken // (Wichtig, damit die Antwort zugestellt werden kann)
                ))
                .addAllTokens(friendTokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            logger.info("Standortanfrage an {} Geräte gesendet.", response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            logger.error("Fehler beim Senden der Standortanfrage", e);
        }
    }

    /**
     * NEU: Sendet die Standort-Antwort eines Freundes (B) zurück an den Anfrager (A).
     * @param targetToken Das Token des Anfragers (User A)
     * @param friend Der User, der seinen Standort meldet (User B)
     * @param latitude Latiude von User B
     * @param longitude Longitude von User B
     */
    @Async
    public void sendLocationRefreshResponse(String targetToken, User friend, double latitude, double longitude) {
        Message message = Message.builder()
                .putAllData(Map.of(
                        "type", "FRIEND_LOCATION_UPDATE",
                        "friendId", friend.getId().toString(),
                        "friendUsername", friend.getUsername(),
                        "latitude", String.valueOf(latitude),
                        "longitude", String.valueOf(longitude)
                ))
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
     * Sends a "ping" notification to a specific target user using Firebase Cloud Messaging (FCM).
     *
     * @param targetToken The FCM token of the target user who will receive the ping notification.
     * @param sender The user who is sending the ping notification.
     * @param senderProfileUrl The URL of the sender's profile image, if available.
     */
    public void sendPingNotification(String targetToken, User sender, String senderProfileUrl) {
        if (targetToken == null || targetToken.isEmpty()) return;

        // Baue die Map für die Daten, null-safe
        Map<String, String> data = new java.util.HashMap<>();
        data.put("type", "FRIEND_PING");
        data.put("senderId", sender.getId().toString());
        data.put("senderUsername", sender.getUsername());
        data.put("timestamp", java.time.OffsetDateTime.now().toString());

        if (senderProfileUrl != null) {
            data.put("senderProfileImageUrl", senderProfileUrl);
        }

        MulticastMessage message = MulticastMessage.builder()
                .addToken(targetToken)
                .putAllData(data)
                .build();

        try {
            FirebaseMessaging.getInstance().sendMulticast(message);
            logger.info("Sent PING from {} to token ending in ...{}", sender.getUsername(), targetToken.substring(Math.max(0, targetToken.length() - 6)));
        } catch (FirebaseMessagingException e) {
            logger.error("Error sending Ping FCM", e);
        }
    }
}