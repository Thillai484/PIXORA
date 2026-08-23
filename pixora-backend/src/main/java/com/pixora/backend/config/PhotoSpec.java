package com.pixora.backend.config;

import lombok.Getter;
import java.awt.Color;

/**
 * Standardized Specifications for Official Document Photos
 * Governs dimensions, aspect ratio, background color, and framing rules.
 */
@Getter
public enum PhotoSpec {

    PASSPORT(
            "Passport",
            600,
            600,
            1.0f,
            new Color(255, 255, 255), // Pure White #FFFFFF
            0.75f, // Face occupies 70-80% of frame (ICAO compliant)
            true
    ),
    VISA(
            "Visa",
            600,
            600,
            1.0f,
            new Color(255, 255, 255), // Pure White #FFFFFF
            0.75f, // ICAO standard
            true
    ),
    COMPANY_ID(
            "Company ID",
            600,
            800,
            0.75f, // 3:4 Ratio
            new Color(240, 240, 240), // Light Gray #F0F0F0
            0.65f, // Shoulders-up crop
            false
    ),
    COLLEGE_ID(
            "College ID",
            600,
            800,
            0.75f, // 3:4 Ratio
            new Color(232, 232, 232), // Neutral Gray #E8E8E8
            0.65f, // Shoulders-up crop
            false
    );

    private final String displayName;
    private final int width;
    private final int height;
    private final float aspectRatio;
    private final Color backgroundColor;
    private final float faceHeightRatio;
    private final boolean requirePureWhite;

    PhotoSpec(String displayName, int width, int height, float aspectRatio, Color backgroundColor, float faceHeightRatio, boolean requirePureWhite) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.aspectRatio = aspectRatio;
        this.backgroundColor = backgroundColor;
        this.faceHeightRatio = faceHeightRatio;
        this.requirePureWhite = requirePureWhite;
    }

    public static PhotoSpec fromType(String photoType) {
        if (photoType == null) return PASSPORT;
        try {
            return PhotoSpec.valueOf(photoType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (photoType.toUpperCase()) {
                case "VISA" -> VISA;
                case "COMPANY_ID" -> COMPANY_ID;
                case "COLLEGE_ID" -> COLLEGE_ID;
                default -> PASSPORT;
            };
        }
    }
}
