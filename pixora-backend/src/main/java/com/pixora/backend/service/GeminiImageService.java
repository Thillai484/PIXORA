package com.pixora.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Google Gemini 2.5 Flash Image Provider Implementation
 * Isolated implementation of AIService for multimodal photo generation
 */
@Slf4j
@Service
@Primary
public class GeminiImageService implements AIService {

    private final String geminiApiKey;
    private final String geminiModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    public GeminiImageService(
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.5-flash-image}") String geminiModel,
            ObjectMapper objectMapper,
            StorageService storageService
    ) {
        this.geminiApiKey = (geminiApiKey != null) ? geminiApiKey.trim() : "";
        this.geminiModel = (geminiModel != null && !geminiModel.isBlank()) ? geminiModel.trim() : "gemini-2.5-flash-image";
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .build();

        if (isConfigured()) {
            log.info("GeminiImageService initialized with model: {}", this.geminiModel);
        } else {
            log.warn("Gemini API key is not configured. Professional AI generation may fail without credentials.");
        }
    }

    public boolean isConfigured() {
        return !geminiApiKey.isBlank() && !geminiApiKey.equalsIgnoreCase("placeholder");
    }

    @Override
    public String generateProfessionalPhoto(String imageUrl, String clothing, String background, String style) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Gemini API key is not configured.");
        }

        // 1. Fetch original image bytes
        byte[] imageBytes = storageService.getFileBytes(imageUrl);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Could not retrieve source image data for AI processing.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = detectMimeType(imageBytes);

        // 2. Build targeted prompt with explicit identity-preservation constraints
        String promptText = String.format(
                "Generate a high-definition professional studio headshot based on this person's photo. " +
                        "Crucial requirements: " +
                        "1. Strictly preserve facial identity, facial structure, eye shape, smile, and skin tone. " +
                        "2. Preserve recognizable facial characteristics and natural features with realistic proportions. " +
                        "3. Avoid excessive beautification, AI plastic skin, or unnatural facial distortions. " +
                        "4. Dress the person in realistic %s. " +
                        "5. Place the person in a %s setting with a %s aesthetic. " +
                        "6. Apply cinematic studio portrait lighting, natural depth-of-field, and realistic fabric textures.",
                clothing != null ? clothing.toLowerCase().replace('_', ' ') : "a tailored corporate blazer and shirt",
                background != null ? background.toLowerCase().replace('_', ' ') : "modern executive office blur",
                style != null ? style.toLowerCase().replace('_', ' ') : "professional corporate headshot"
        );

        // 3. Build multimodal Gemini request payload
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObject = contentsArray.addObject();
        ArrayNode partsArray = contentObject.putArray("parts");

        // Text prompt part
        ObjectNode textPart = partsArray.addObject();
        textPart.put("text", promptText);

        // Inline image data part
        ObjectNode imagePart = partsArray.addObject();
        ObjectNode inlineData = imagePart.putObject("inlineData");
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", base64Image);

        // Generation config for image output
        ObjectNode genConfig = rootNode.putObject("generationConfig");
        genConfig.put("responseMimeType", "image/png");

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        // 4. Call Gemini API with 1 retry for transient 429/5xx errors
        byte[] generatedBytes = executeWithRetry(jsonPayload, 1);

        if (generatedBytes == null || generatedBytes.length == 0) {
            throw new RuntimeException("Gemini returned empty or unparseable image content.");
        }

        // 5. Upload generated result to Supabase Storage
        String filename = String.format("gemini-portrait-%d.png", System.currentTimeMillis());
        return storageService.uploadGeneratedPhoto(1L, filename, generatedBytes, "image/png");
    }

    @Override
    public Map<String, String> generatePhotoPack(String imageUrl, List<String> photoTypes) throws Exception {
        Map<String, String> resultMap = new LinkedHashMap<>();

        for (String type : photoTypes) {
            String clothing = switch (type.toUpperCase()) {
                case "PASSPORT", "VISA", "COLLEGE_ID" -> "formal collared shirt";
                case "COMPANY_ID" -> "smart business attire with blazer";
                case "LINKEDIN" -> "tailored modern blazer with open collar";
                case "RESUME" -> "classic dark executive suit and tie";
                default -> "professional corporate attire";
            };

            String background = switch (type.toUpperCase()) {
                case "PASSPORT", "VISA" -> "solid clean pure white studio background";
                case "COLLEGE_ID", "COMPANY_ID" -> "neutral light gray studio backdrop";
                case "LINKEDIN" -> "modern creative office workspace with soft bokeh";
                case "RESUME" -> "corporate studio gradient backdrop";
                default -> "executive studio lighting background";
            };

            String style = switch (type.toUpperCase()) {
                case "PASSPORT", "VISA" -> "standardized biometric document photo";
                case "LINKEDIN" -> "approachable, high-trust networking headshot";
                case "RESUME" -> "polished executive recruiter headshot";
                default -> "professional studio portrait";
            };

            String url = generateProfessionalPhoto(imageUrl, clothing, background, style);
            resultMap.put(type, url);
        }

        return resultMap;
    }

    /**
     * Execute Gemini HTTP request with single retry for 429 rate limit or 5xx server errors
     */
    private byte[] executeWithRetry(String jsonPayload, int maxRetries) throws Exception {
        String endpoint = String.format("%s%s:generateContent?key=%s", GEMINI_API_BASE, geminiModel, geminiApiKey);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(45))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return parseImageFromGeminiResponse(response.body());
                }

                log.warn("Gemini API returned status {} on attempt {}: {}", status, attempt + 1, response.body());

                // Check for rate limit (429) or transient server error (500, 503)
                if ((status == 429 || status >= 500) && attempt < maxRetries) {
                    log.info("Retrying Gemini API call in 2 seconds...");
                    Thread.sleep(2000);
                    continue;
                }

                // If gemini-2.5-flash-image returned 404, fallback to imagen-3.0 or gemini-2.0
                if (status == 404 && attempt < maxRetries) {
                    endpoint = String.format("%sgemini-2.0-flash-exp:generateContent?key=%s", GEMINI_API_BASE, geminiApiKey);
                    continue;
                }

                throw new RuntimeException("Gemini API call failed with status " + status);

            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    throw e;
                }
                log.warn("Transient error calling Gemini API: {}. Retrying...", e.getMessage());
                Thread.sleep(2000);
            }
        }

        return null;
    }

    /**
     * Parse inline base64 image data from Gemini JSON response
     */
    private byte[] parseImageFromGeminiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Path 1: candidates[0].content.parts[0].inlineData.data
            if (root.has("candidates") && root.get("candidates").isArray() && root.get("candidates").size() > 0) {
                JsonNode candidate = root.get("candidates").get(0);
                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    for (JsonNode part : candidate.get("content").get("parts")) {
                        if (part.has("inlineData") && part.get("inlineData").has("data")) {
                            String base64 = part.get("inlineData").get("data").asText();
                            return Base64.getDecoder().decode(base64);
                        }
                        if (part.has("inline_data") && part.get("inline_data").has("data")) {
                            String base64 = part.get("inline_data").get("data").asText();
                            return Base64.getDecoder().decode(base64);
                        }
                    }
                }
            }

            // Path 2: predictions[0].bytesBase64Encoded (Imagen format)
            if (root.has("predictions") && root.get("predictions").isArray() && root.get("predictions").size() > 0) {
                JsonNode pred = root.get("predictions").get(0);
                if (pred.has("bytesBase64Encoded")) {
                    String base64 = pred.get("bytesBase64Encoded").asText();
                    return Base64.getDecoder().decode(base64);
                }
            }

            // Path 3: images[0].url or base64
            if (root.has("images") && root.get("images").isArray() && root.get("images").size() > 0) {
                String imgStr = root.get("images").get(0).asText();
                if (imgStr.startsWith("data:")) {
                    String base64 = imgStr.substring(imgStr.indexOf(",") + 1);
                    return Base64.getDecoder().decode(base64);
                }
            }

            log.warn("Could not find inlineData in Gemini response. Response body preview: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
            return null;

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
