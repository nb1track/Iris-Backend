package com.chaptime.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class FirebaseConfig {

    /**
     * Represents a reference to the Firebase service account credentials file.
     * This file is used to authenticate the application with Firebase services.
     * The file path is injected from the application properties using the key `firebase.service-account.file-path`.
     *
     * The resource is utilized in the Firebase initialization process to load credentials
     * and establish a connection to Firebase services.
     */
    @Value("${firebase.service-account.file-path}")
    private Resource serviceAccountResource;

    /**
     * Initializes the Firebase application using the provided service account credentials.
     * If no Firebase application has been initialized yet, it creates a new instance
     * using the service account file. If a Firebase instance already exists,
     * the existing instance is returned.
     *
     * @return The initialized Firebase application instance.
     * @throws IOException If an error occurs while reading the service account file.
     */
    @Bean
    public FirebaseApp initializeFirebase() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountResource.getInputStream()))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }
}