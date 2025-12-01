package com.iris.backend.service;

import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class GcsStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GcsStorageService.class);
    private final Storage storage;

    // Bucket-Namen aus der Konfiguration laden
    private final String photosBucketName;
    private final String profileImagesBucketName;

    /**
     * Constructs a new GcsStorageService.
     * Bucket names are injected from application properties.
     */
    public GcsStorageService(Storage storage,
                             @Value("${gcs.bucket.photos.name}") String photosBucketName,
                             @Value("${gcs.bucket.profile-images.name}") String profileImagesBucketName) {
        this.storage = storage;
        this.photosBucketName = photosBucketName;
        this.profileImagesBucketName = profileImagesBucketName;
    }

    /**
     * Uploads a photo file to the photos bucket.
     *
     * @param file the photo file to upload.
     * @return The unique object name of the uploaded file.
     * @throws IOException if an I/O error occurs.
     */
    public String uploadPhoto(MultipartFile file) throws IOException {
        String objectName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
        BlobId blobId = BlobId.of(photosBucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        logger.info("Successfully uploaded photo {} to bucket {}", objectName, photosBucketName);
        return objectName; // WICHTIG: Nur den Objektnamen zurückgeben
    }

    /**
     * Uploads a profile image to the profile images bucket.
     *
     * @param uid        The user's unique identifier, used as the file name.
     * @return The object name of the uploaded file (e.g., "some-uid.jpg").
     */
    public String uploadProfileImage(String uid, MultipartFile file) throws IOException {
        // WICHTIG: Wir hängen eine UUID an, damit sich der Dateiname bei jedem Upload ändert.
        // Das zwingt die App, das Bild neu zu laden.
        String originalFilename = file.getOriginalFilename();
        String extension = "jpg"; // Default
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        }

        // Dateiname: UID + Zufallswert + Endung (z.B. "user123-a1b2c3d4.jpg")
        String objectName = uid + "-" + UUID.randomUUID().toString() + "." + extension;

        BlobId blobId = BlobId.of(profileImagesBucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType()) // Nutzt den echten Content-Type (z.B. image/png)
                .build();

        storage.create(blobInfo, file.getBytes());
        logger.info("Successfully uploaded profile image {} to bucket {}", objectName, profileImagesBucketName);
        return objectName;
    }

    public String uploadProfileImage(String uid, byte[] imageBytes) {
        // Auch hier hängen wir jetzt eine UUID an, um konsistent zu bleiben
        String objectName = uid + "-" + UUID.randomUUID().toString() + ".jpg";

        BlobId blobId = BlobId.of(profileImagesBucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("image/jpeg")
                .build();

        storage.create(blobInfo, imageBytes);
        logger.info("Successfully uploaded profile image (byte[]) {} to bucket {}", objectName, profileImagesBucketName);
        return objectName;
    }

    /**
     * Deletes an object from a specified bucket.
     *
     * @param bucketName The name of the bucket.
     * @param objectName The name of the object to delete.
     */
    public void deleteFile(String bucketName, String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        try {
            BlobId blobId = BlobId.of(bucketName, objectName);
            if (storage.delete(blobId)) {
                logger.info("Successfully deleted file {} from bucket {}", objectName, bucketName);
            } else {
                logger.warn("File {} not found in bucket {} for deletion.", objectName, bucketName);
            }
        } catch (Exception e) {
            logger.error("Failed to delete file {} from bucket {}: {}", objectName, bucketName, e.getMessage());
        }
    }

    /**
     * Generates a signed URL for an object in a specified bucket with a custom expiration.
     * This is now the single, flexible method for creating all signed URLs.
     *
     * @param bucketName The bucket containing the object.
     * @param objectName The name of the object.
     * @param duration   The numerical value of the duration.
     * @param timeUnit   The unit of the duration (e.g., TimeUnit.MINUTES).
     * @return A temporary signed URL, or null if an error occurs.
     */
    public String generateSignedUrl(String bucketName, String objectName, long duration, TimeUnit timeUnit) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }

        // --- HIER IST DIE WICHTIGE ABSICHERUNG ---
        // Wenn der objectName eine volle URL ist, extrahieren wir nur den Dateinamen.
        // Das macht den Code robust gegenüber alten, falsch gespeicherten Daten.
        String finalObjectName = objectName;
        if (objectName.startsWith("http")) {
            try {
                finalObjectName = objectName.substring(objectName.lastIndexOf('/') + 1);
            } catch (Exception e) {
                logger.error("Could not parse object name from full URL: {}", objectName);
                return null; // Im Fehlerfall abbrechen
            }
        }

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, finalObjectName)).build();
            URL signedUrl = storage.signUrl(blobInfo, duration, timeUnit, Storage.SignUrlOption.withV4Signature());
            return signedUrl.toExternalForm();
        } catch (Exception e) {
            logger.error("Could not generate signed URL for object {} in bucket {}: {}", finalObjectName, bucketName, e.getMessage());
            return null;
        }
    }
}