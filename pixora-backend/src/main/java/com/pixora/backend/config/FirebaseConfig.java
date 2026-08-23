package com.pixora.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.project-id:}")
    private String projectId;

    private boolean initialized = false;

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            this.initialized = true;
            return;
        }

        try {
            InputStream serviceAccountStream = null;

            // 1. Try file path
            if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                File file = new File(serviceAccountPath);
                if (file.exists() && file.isFile()) {
                    serviceAccountStream = new FileInputStream(file);
                    log.info("Loading Firebase credentials from file: {}", serviceAccountPath);
                } else {
                    log.warn("Firebase service account file not found at: {}", serviceAccountPath);
                }
            }

            // 2. Try JSON environment string
            if (serviceAccountStream == null && serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                serviceAccountStream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
                log.info("Loading Firebase credentials from FIREBASE_SERVICE_ACCOUNT_JSON environment variable");
            }

            if (serviceAccountStream != null) {
                FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccountStream));

                if (projectId != null && !projectId.isBlank()) {
                    optionsBuilder.setProjectId(projectId);
                }

                FirebaseApp.initializeApp(optionsBuilder.build());
                this.initialized = true;
                log.info("FirebaseApp initialized successfully.");
            } else {
                log.warn("No Firebase credentials provided. Mock/Development auth mode is active.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp: {}", e.getMessage(), e);
        }
    }

    public boolean isInitialized() {
        return initialized && !FirebaseApp.getApps().isEmpty();
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        if (isInitialized()) {
            return FirebaseAuth.getInstance();
        }
        return null;
    }
}
