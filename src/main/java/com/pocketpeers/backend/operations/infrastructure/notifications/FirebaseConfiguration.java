package com.pocketpeers.backend.operations.infrastructure.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfiguration {
    @Value("${firebase.service-account.path:}")
    private String serviceAccountPath;

    private final ResourceLoader resourceLoader;

    public FirebaseConfiguration(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank() || !FirebaseApp.getApps().isEmpty()) {
            return;
        }

        var credentials = resolveCredentialsResource();
        if (!credentials.exists()) {
            throw new IllegalStateException("Firebase service account file not found: " + serviceAccountPath);
        }

        try (var serviceAccount = credentials.getInputStream()) {
            var options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (Exception exc) {
            throw new IllegalStateException("Could not initialize Firebase Admin SDK", exc);
        }
    }

    private Resource resolveCredentialsResource() {
        if (serviceAccountPath.startsWith("classpath:")) {
            return resourceLoader.getResource(serviceAccountPath);
        }
        return new FileSystemResource(serviceAccountPath);
    }
}
