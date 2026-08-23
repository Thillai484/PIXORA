package com.pixora.backend.service;

import java.util.List;
import java.util.Map;

/**
 * Pluggable AI Generation Interface
 * Keeps all AI provider implementations swappable and decoupled.
 */
public interface AIService {

    /**
     * Generate a single professional studio portrait based on original image and custom attributes
     *
     * @param imageUrl   The source image URL or data URI
     * @param clothing   Desired attire (e.g. "Tailored dark blazer", "Formal shirt and tie", "Executive suit")
     * @param background Desired background (e.g. "Modern office interior with soft bokeh", "Clean studio backdrop")
     * @param style      Visual style (e.g. "Corporate headshot", "Executive portrait", "Studio portrait")
     * @return Generated image public URL
     */
    String generateProfessionalPhoto(String imageUrl, String clothing, String background, String style) throws Exception;

    /**
     * Generate a batch pack of professional portraits across multiple photo types
     *
     * @param imageUrl   The source image URL or data URI
     * @param photoTypes List of preset photo types (e.g. ["RESUME", "LINKEDIN", "COMPANY_ID"])
     * @return Map of photoType to generated public URL
     */
    Map<String, String> generatePhotoPack(String imageUrl, List<String> photoTypes) throws Exception;
}
