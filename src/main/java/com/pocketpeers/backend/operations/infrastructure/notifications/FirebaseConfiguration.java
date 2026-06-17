package com.pocketpeers.backend.operations.infrastructure.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfiguration {

    @Value("${firebase.service-account.path:}")
    private Resource serviceAccountResource;

    @PostConstruct
    public void initializeFirebase() {
        if (serviceAccountResource == null || !FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (var serviceAccount = serviceAccountResource.getInputStream()) {
            var options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (Exception exc) {
            throw new IllegalStateException("Could not initialize Firebase Admin SDK", exc);
        }
    }
}