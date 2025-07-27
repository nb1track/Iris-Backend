package com.iris.backend.config;

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

    /**
     * Represents the Firebase service account JSON configuration required to initialize
     * a FirebaseApp instance within the application. This value is injected from the
     * environment variable `SPRING_APPLICATION_JSON`.
     *
     * The JSON typically contains configuration details, such as the private key and
     * client email, necessary for authenticating with Firebase services.
     *
     * If this value is not provided or empty, the application will fail to initialize
     * Firebase and throw an {@link IllegalStateException}.
     */
    @Value("${SPRING_APPLICATION_JSON:}")
    private String firebaseServiceAccountJson;

    /**
     * Initializes and configures a FirebaseApp instance for the application.
     * If a FirebaseApp instance has already been initialized, it returns the existing instance.
     *
     * This method uses the Firebase*/
    @Bean
    public FirebaseApp initializeFirebase() throws IOException {
        if (firebaseServiceAccountJson == null || firebaseServiceAccountJson.isEmpty()) {
            // Diese Exception wird geworfen, wenn die Umgebungsvariable nicht gesetzt ist.
            // Nicht, wenn eine Datei nicht gefunden wird.
            throw new IllegalStateException("Firebase service account JSON not found in SPRING_APPLICATION_JSON environment variable. Please ensure it's set in Cloud Run.");
        }

        // Hier wird der String in einen InputStream umgewandelt
        InputStream serviceAccount = new ByteArrayInputStream(firebaseServiceAccountJson.getBytes(StandardCharsets.UTF_8));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                // .setDatabaseUrl("https://your-project-id.firebaseio.com") // Optional: uncomment if you use Realtime Database or older Firebase features
                .build();

        // Initialisiere die App nur, wenn sie noch nicht initialisiert wurde
        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        } else {
            return FirebaseApp.getInstance(); // Gib die bereits initialisierte App zurück
        }
    }
}