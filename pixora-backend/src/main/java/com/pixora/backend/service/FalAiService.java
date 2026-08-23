package com.pixora.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FalAiService {

    private final String falApiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Standard fal.ai Flux Image-to-Image endpoint
    private static final String FAL_QUEUE_URL = "https://queue.fal.run/fal-ai/flux/dev/image-to-image";

    public FalAiService(
            @Value("${fal.api-key:}") String falApiKey,
            ObjectMapper objectMapper
    ) {
        this.falApiKey = falApiKey != null ? falApiKey.trim() : "";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        if (isConfigured()) {
            log.info("FalAiService initialized with live fal.ai API key");
        } else {
            log.warn("fal.ai API key not configured. Mock/Fallback AI generation mode is active.");
        }
    }

    public boolean isConfigured() {
        return !falApiKey.isBlank() && !falApiKey.contains("your-fal-key") && !falApiKey.equalsIgnoreCase("placeholder");
    }

    /**
     * Submit image-to-image transformation to fal.ai and wait for the result
     */
    public byte[] generatePortrait(String inputImageUrl, String prompt, String negativePrompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("fal.ai API key is not configured.");
        }

        // Build fal.ai payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("image_url", inputImageUrl);
        payload.put("prompt", prompt);
        payload.put("negative_prompt", negativePrompt);
        payload.put("strength", 0.65);
        payload.put("guidance_scale", 7.5);
        payload.put("num_inference_steps", 28);
        payload.put("enable_safety_checker", true);

        String jsonBody = objectMapper.writeValueAsString(payload);

        log.info("Submitting generation request to fal.ai for image: {}", inputImageUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FAL_QUEUE_URL))
                .header("Authorization", "Key " + falApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> queueResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (queueResponse.statusCode() < 200 || queueResponse.statusCode() >= 300) {
            log.error("fal.ai queue submission failed with status {}: {}", queueResponse.statusCode(), queueResponse.body());
            throw new RuntimeException("fal.ai queue failed: " + queueResponse.body());
        }

        JsonNode queueNode = objectMapper.readTree(queueResponse.body());

        // Check if direct synchronous response contains images
        if (queueNode.has("images") && queueNode.get("images").isArray() && queueNode.get("images").size() > 0) {
            String imageUrl = queueNode.get("images").get(0).get("url").asText();
            return downloadImageBytes(imageUrl);
        }

        // Asynchronous queue handling
        String requestId = queueNode.has("request_id") ? queueNode.get("request_id").asText() : null;
        String statusUrl = queueNode.has("status_url") ? queueNode.get("status_url").asText() :
                (requestId != null ? "https://queue.fal.run/fal-ai/flux/dev/requests/" + requestId + "/status" : null);
        String responseUrl = queueNode.has("response_url") ? queueNode.get("response_url").asText() :
                (requestId != null ? "https://queue.fal.run/fal-ai/flux/dev/requests/" + requestId : null);

        if (statusUrl == null || responseUrl == null) {
            throw new RuntimeException("fal.ai did not return valid status/response URLs.");
        }

        // Poll for completion (max 60 seconds)
        int maxAttempts = 30;
        int delayMs = 2000;
        for (int i = 0; i < maxAttempts; i++) {
            Thread.sleep(delayMs);

            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Key " + falApiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> statusRes = httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString());
            if (statusRes.statusCode() == 200) {
                JsonNode statusNode = objectMapper.readTree(statusRes.body());
                String status = statusNode.has("status") ? statusNode.get("status").asText() : "IN_PROGRESS";

                if ("COMPLETED".equalsIgnoreCase(status)) {
                    // Fetch final result
                    HttpRequest resReq = HttpRequest.newBuilder()
                            .uri(URI.create(responseUrl))
                            .header("Authorization", "Key " + falApiKey)
                            .GET()
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    HttpResponse<String> finalRes = httpClient.send(resReq, HttpResponse.BodyHandlers.ofString());
                    JsonNode finalNode = objectMapper.readTree(finalRes.body());

                    if (finalNode.has("images") && finalNode.get("images").size() > 0) {
                        String generatedUrl = finalNode.get("images").get(0).get("url").asText();
                        return downloadImageBytes(generatedUrl);
                    }
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    throw new RuntimeException("fal.ai job failed: " + statusRes.body());
                }
            }
        }

        throw new RuntimeException("fal.ai generation timed out.");
    }

    /**
     * Download generated image bytes from remote URL
     */
    public byte[] downloadImageBytes(String imageUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to download image from: " + imageUrl);
        }
    }
}
