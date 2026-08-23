package com.pixora.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * LightX Editor AI Headshot Generation Provider
 * Generates distinct AI headshots and corporate portraits using per-preset prompt templates.
 */
@Slf4j
@Service
@Primary
public class LightXImageService implements AIService {

    private final String lightxApiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private static final String LIGHTX_HEADSHOT_URL = "https://api.lightxeditor.com/external/api/v2/headshot/";
    private static final String LIGHTX_STATUS_URL = "https://api.lightxeditor.com/external/api/v2/order-status/";

    public LightXImageService(
            @Value("${lightx.api-key:}") String lightxApiKey,
            ObjectMapper objectMapper,
            StorageService storageService
    ) {
        this.lightxApiKey = (lightxApiKey != null) ? lightxApiKey.trim() : "";
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        if (isConfigured()) {
            log.info("LightXImageService initialized with live LightX API key");
        } else {
            log.warn("LightX API key is not configured.");
        }
    }

    public boolean isConfigured() {
        return !lightxApiKey.isBlank() && !lightxApiKey.equalsIgnoreCase("placeholder");
    }

    @Override
    public String generateProfessionalPhoto(String imageUrl, String clothing, String background, String style) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("LightX Editor API key is not configured.");
        }

        // Build distinct prompt template
        String textPrompt;
        if ("LINKEDIN".equalsIgnoreCase(style)) {
            textPrompt = "approachable professional headshot, business casual blazer, softly blurred modern office background, warm natural lighting, slight friendly smile, shoulders angled slightly, networking profile photo style";
        } else if ("JOB_APPLICATION".equalsIgnoreCase(style) || "CAREER".equalsIgnoreCase(style)) {
            textPrompt = "clean professional headshot, business formal attire, solid light blue or gray background, bright even lighting, formal neutral expression, passport-adjacent but softer styling";
        } else if ("RESUME".equalsIgnoreCase(style) || "CV".equalsIgnoreCase(style)) {
            textPrompt = "professional corporate headshot, dark navy blazer over collared shirt, plain studio gray background, soft even studio lighting, direct eye contact, neutral confident expression, sharp focus, high resolution corporate portrait photography";
        } else {
            String clothingDesc = (clothing != null && !clothing.isBlank())
                    ? clothing.toLowerCase().replace('_', ' ')
                    : "navy blazer and crisp formal shirt";
            String bgDesc = (background != null && !background.isBlank())
                    ? background.toLowerCase().replace('_', ' ')
                    : "clean executive studio background";
            String styleDesc = (style != null && !style.isBlank())
                    ? style.toLowerCase().replace('_', ' ')
                    : "professional corporate headshot";

            textPrompt = String.format("%s, %s, %s, sharp focus, natural studio lighting, high resolution portrait",
                    styleDesc, clothingDesc, bgDesc);
        }

        log.info("LIGHTX_PROMPT [{}] -> '{}'", style != null ? style : "CUSTOM", textPrompt);

        // 1. Submit Headshot Generation Request to LightX
        Map<String, String> payload = new HashMap<>();
        payload.put("imageUrl", imageUrl);
        payload.put("textPrompt", textPrompt);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LIGHTX_HEADSHOT_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", lightxApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("LightX API submit failed with status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("LightX Headshot submission failed: " + response.body());
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        String orderId = null;
        if (rootNode.has("body") && rootNode.get("body").has("orderId")) {
            orderId = rootNode.get("body").get("orderId").asText();
        }

        if (orderId == null || orderId.isBlank()) {
            throw new RuntimeException("LightX did not return a valid orderId: " + response.body());
        }

        log.info("LightX task submitted successfully. Order ID: {}. Polling status...", orderId);

        // 2. Poll Order Status until active/complete
        String finalImageUrl = pollOrderStatus(orderId);

        if (finalImageUrl == null || finalImageUrl.isBlank()) {
            throw new RuntimeException("LightX did not produce a generated image URL.");
        }

        log.info("LightX generation succeeded! Output image: {}", finalImageUrl);

        // 3. Download bytes and store permanently in Supabase
        byte[] imageBytes = storageService.getFileBytes(finalImageUrl);
        if (imageBytes != null && imageBytes.length > 0) {
            String filename = String.format("photo-lightx-%s-%d.jpg",
                    style != null ? style.toLowerCase() : "headshot", System.currentTimeMillis());
            return storageService.uploadGeneratedPhoto(1L, filename, imageBytes, "image/jpeg");
        }

        return finalImageUrl;
    }

    @Override
    public Map<String, String> generatePhotoPack(String imageUrl, List<String> photoTypes) throws Exception {
        Map<String, String> resultMap = new LinkedHashMap<>();

        for (String type : photoTypes) {
            String clothing = switch (type.toUpperCase()) {
                case "COMPANY_ID" -> "smart business attire with blazer";
                case "LINKEDIN" -> "modern tailored blazer";
                case "RESUME" -> "classic dark executive suit and tie";
                default -> "professional corporate attire";
            };

            String background = switch (type.toUpperCase()) {
                case "COMPANY_ID" -> "neutral light gray studio backdrop";
                case "LINKEDIN" -> "modern office interior with soft blur";
                case "RESUME" -> "corporate studio gradient backdrop";
                default -> "studio lighting background";
            };

            String url = generateProfessionalPhoto(imageUrl, clothing, background, type);
            resultMap.put(type, url);
        }

        return resultMap;
    }

    /**
     * Poll LightX Order Status until generation is finished (Max 60 seconds)
     */
    private String pollOrderStatus(String orderId) throws Exception {
        Map<String, String> statusReqMap = Map.of("orderId", orderId);
        String statusJson = objectMapper.writeValueAsString(statusReqMap);

        int maxAttempts = 30;
        int delayMs = 2500;

        for (int i = 0; i < maxAttempts; i++) {
            Thread.sleep(delayMs);

            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create(LIGHTX_STATUS_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", lightxApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(statusJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> statusRes = httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString());

            if (statusRes.statusCode() == 200) {
                JsonNode resNode = objectMapper.readTree(statusRes.body());
                if (resNode.has("body")) {
                    JsonNode body = resNode.get("body");
                    String status = body.has("status") ? body.get("status").asText() : "";

                    if (body.has("output") && !body.get("output").isNull() && !body.get("output").asText().isBlank()) {
                        return body.get("output").asText();
                    }

                    if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                        throw new RuntimeException("LightX order failed: " + statusRes.body());
                    }
                }
            } else {
                log.warn("Status poll attempt {} failed with status: {}", i + 1, statusRes.statusCode());
            }
        }

        throw new RuntimeException("LightX Headshot generation timed out.");
    }
}
