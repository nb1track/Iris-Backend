package com.iris.backend.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Service
public class GcsStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GcsStorageService.class);
    private final Storage storage;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    /**
     * Constructs a new {@code GcsStorageService} with the specified Google Cloud Storage client.
     *
     * @param storage the Google Cloud Storage client used for interacting with the storage bucket
     */
    public GcsStorageService(Storage storage) {
        this.storage = storage;
    }

    /**
     * Uploads a file to the configured Google Cloud Storage bucket and generates a public URL for the uploaded file.
     *
     * @param file the file to be uploaded; must be a valid {@code MultipartFile}
     * @return a public URL where the uploaded file can be accessed
     * @throws IOException if an error occurs during file upload or reading file data
     */
    public String uploadFile(MultipartFile file) throws IOException {
        String uniqueFileName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
        BlobId blobId = BlobId.of(bucketName, uniqueFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, file.getBytes());
        logger.info("Successfully uploaded file {} to bucket {}", uniqueFileName, bucketName);
        return "https://storage.googleapis.com/" + bucketName + "/" + uniqueFileName;
    }

    /**
     * Deletes a file from the configured Google Cloud Storage bucket.
     * If the specified file does not exist in the bucket, a warning is logged.
     * If an error occurs during the deletion process, the error is logged.
     *
     * @param fileUrl the public URL of the file to be deleted; the method extracts the file name
     *                from the URL to locate and delete the file in the bucket
     */
    public void deleteFile(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            BlobId blobId = BlobId.of(bucketName, fileName);
            if (storage.delete(blobId)) {
                logger.info("Successfully deleted file {} from bucket {}", fileName, bucketName);
            } else {
                logger.warn("File {} not found in bucket {} for deletion.", fileName, bucketName);
            }
        } catch (Exception e) {
            logger.error("Failed to delete file {}: {}", fileUrl, e.getMessage());
        }
    }

    /**
     * Generiert eine zeitlich begrenzte, signierte URL für ein privates GCS-Objekt.
     * @param fileUrl Die permanente URL des Objekts (z.B. aus der Datenbank)
     * @return Eine temporäre URL, die für 15 Minuten gültig ist.
     */
    public String generateSignedUrl(String fileUrl) {
        try {
            // Extrahiert den Dateinamen aus der URL
            String objectName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            // Generiert eine URL mit V4-Signatur, die 15 Minuten gültig ist
            URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());

            return signedUrl.toString();
        } catch (Exception e) {
            logger.error("Could not generate signed URL for {}", fileUrl, e);
            // Gibt im Fehlerfall die originale URL zurück, die aber nicht funktionieren wird
            return fileUrl;
        }
    }
}