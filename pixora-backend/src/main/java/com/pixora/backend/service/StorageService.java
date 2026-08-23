package com.pixora.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    private final String supabaseUrl;
    private final String supabaseKey;
    private final HttpClient httpClient;
    private static final String BUCKET_NAME = "photos";

    public StorageService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.key:}") String supabaseKey
    ) {
        this.supabaseUrl = (supabaseUrl != null) ? supabaseUrl.replaceAll("/+$", "") : "";
        this.supabaseKey = (supabaseKey != null) ? supabaseKey.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        if (isConfigured()) {
            log.info("StorageService initialized with Supabase endpoint: {}", this.supabaseUrl);
        } else {
            log.warn("Supabase credentials not configured. Local fallback storage mode is active.");
        }
    }

    public boolean isConfigured() {
        return !supabaseUrl.isBlank() && !supabaseKey.isBlank() && !supabaseUrl.contains("your-project");
    }

    /**
     * Upload an original user photo to Supabase Storage
     * Path: photos/{userId}/original/{uniqueName}
     */
    public String uploadOriginalPhoto(Long userId, String originalFilename, byte[] fileBytes, String contentType) {
        String ext = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + (ext.isEmpty() ? ".jpg" : ext);
        String relativePath = "photos/" + userId + "/original/" + uniqueFilename;

        return uploadFile(relativePath, fileBytes, contentType != null ? contentType : "image/jpeg");
    }

    /**
     * Upload an AI-generated photo to Supabase Storage
     * Path: photos/{userId}/generated/{uniqueName}
     */
    public String uploadGeneratedPhoto(Long userId, String filename, byte[] imageBytes, String contentType) {
        String ext = getFileExtension(filename);
        String uniqueFilename = UUID.randomUUID().toString() + (ext.isEmpty() ? ".png" : ext);
        String relativePath = "photos/" + userId + "/generated/" + uniqueFilename;

        return uploadFile(relativePath, imageBytes, contentType != null ? contentType : "image/png");
    }

    /**
     * Generic file upload method to Supabase REST Storage API
     */
    public String uploadFile(String objectPath, byte[] data, String contentType) {
        if (!isConfigured()) {
            return saveLocalFallback(objectPath, data);
        }

        try {
            // Supabase REST endpoint: POST /storage/v1/object/{bucket}/{path}
            String uploadEndpoint = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, BUCKET_NAME, objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadEndpoint))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", contentType)
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String publicUrl = getPublicUrl(objectPath);
                log.info("Successfully uploaded file to Supabase Storage: {}", publicUrl);
                return publicUrl;
            } else {
                log.error("Supabase Storage upload failed with status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Supabase Storage upload failed: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error during Supabase Storage upload, falling back to local storage: {}", e.getMessage());
            return saveLocalFallback(objectPath, data);
        }
    }

    /**
     * Retrieve file bytes from URL or local storage fallback
     */
    public byte[] getFileBytes(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isBlank()) {
                return new byte[0];
            }

            if (fileUrl.contains("/storage/photos/")) {
                String subPath = fileUrl.substring(fileUrl.indexOf("/storage/photos/") + "/storage/photos/".length());
                Path localPath = Paths.get("target", "storage", BUCKET_NAME, subPath);
                if (Files.exists(localPath)) {
                    return Files.readAllBytes(localPath);
                }
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return res.body();
            }
            return new byte[0];
        } catch (Exception e) {
            log.warn("Failed to get file bytes from {}: {}", fileUrl, e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Delete files from Supabase Storage
     */
    public void deleteFile(String objectPath) {
        if (!isConfigured() || objectPath == null || objectPath.isBlank()) return;

        try {
            String path = objectPath;
            String publicPrefix = String.format("%s/storage/v1/object/public/%s/", supabaseUrl, BUCKET_NAME);
            if (path.startsWith(publicPrefix)) {
                path = path.substring(publicPrefix.length());
            }

            String deleteEndpoint = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, BUCKET_NAME, path);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteEndpoint))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .DELETE()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Failed to delete file from Supabase: {}", e.getMessage());
        }
    }

    /**
     * Construct public CDN URL for an object in the "photos" bucket
     */
    public String getPublicUrl(String objectPath) {
        return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, BUCKET_NAME, objectPath);
    }

    /**
     * Save to local disk directory if Supabase is not yet configured
     */
    private String saveLocalFallback(String objectPath, byte[] data) {
        try {
            Path targetDir = Paths.get("target", "storage", BUCKET_NAME, objectPath).getParent();
            if (targetDir != null) {
                Files.createDirectories(targetDir);
            }
            Path targetFile = Paths.get("target", "storage", BUCKET_NAME, objectPath);
            Files.write(targetFile, data);

            String localUrl = "http://localhost:8080/storage/" + BUCKET_NAME + "/" + objectPath;
            log.info("Saved file to local storage fallback: {}", localUrl);
            return localUrl;
        } catch (Exception e) {
            log.error("Failed to save local fallback: {}", e.getMessage());
            return "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&auto=format&fit=crop";
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
