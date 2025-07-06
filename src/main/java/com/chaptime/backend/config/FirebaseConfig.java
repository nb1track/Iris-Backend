package com.chaptime.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    // Die Umgebungsvariable SPRING_APPLICATION_JSON wird automatisch von Spring Boot geladen.
    // Wir injizieren sie hier direkt als String.
    // WICHTIG: Stelle sicher, dass die Umgebungsvariable in Cloud Run als SPRING_APPLICATION_JSON gesetzt ist
    // und den vollständigen JSON-Inhalt deines Firebase Service Accounts enthält.
    @Value("${SPRING_APPLICATION_JSON:}") // Standardwert ist leerer String, falls nicht gesetzt
    private String firebaseServiceAccountJson;

    /**
     * Initializes and configures a FirebaseApp instance using the service account JSON
     * provided through the `SPRING_APPLICATION_JSON` environment variable.
     *
     * This method is responsible for setting up FirebaseOptions with the credentials
     * parsed from the service account JSON. The Firebase application is then initialized
     * with these options. If the `SPRING_APPLICATION_JSON` environment variable is not
     * set or contains an empty value, the method will throw an {@link IllegalStateException}.
     *
     * @return A {@link FirebaseApp} instance configured with the specified credentials.
     * @throws IOException If an I/O error occurs while processing the service account JSON.
     * @throws IllegalStateException If the required environment variable is not set or is empty.
     */
    @Bean
    public FirebaseApp initializeFirebase() throws IOException {
        if (firebaseServiceAccountJson == null || firebaseServiceAccountJson.isEmpty()) {
            throw new IllegalStateException("Firebase service account JSON not found in SPRING_APPLICATION_JSON environment variable.");
        }

        InputStream serviceAccount = new ByteArrayInputStream(firebaseServiceAccountJson.getBytes(StandardCharsets.UTF_8));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                // Optional: .setDatabaseUrl("https://your-project-id.firebaseio.com")
                .build();

        return FirebaseApp.initializeApp(options);
    }
}