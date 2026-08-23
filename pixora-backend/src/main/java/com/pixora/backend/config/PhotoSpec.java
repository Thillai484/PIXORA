package com.pixora.backend.config;

import lombok.Getter;
import java.awt.Color;

/**
 * Exact Specifications for Official Document Photos
 * Governs dimensions, aspect ratio, background color, framing, and consular contrast settings per preset.
 */
@Getter
public enum PhotoSpec {

    PASSPORT(
            "Passport Photo",
            600,
            600,
            1.0f,
            new Color(255, 255, 255), // Pure White #FFFFFF
            0.76f, // Face height 70-80% of frame height (ICAO compliant)
            0.22f, // Eyes at ~55-60% from bottom
            1.04f, // Standard contrast
            0.0f,  // Neutral warmth
            "2x2 in (51x51mm) • Pure White #FFFFFF • ICAO Compliant"
    ),

    VISA_US(
            "US / India Visa",
            600,
            600,
            1.0f,
            new Color(255, 255, 255), // Pure White #FFFFFF
            0.75f, // 70-80%
            0.22f,
            1.10f, // Higher contrast for consular scanners
            0.0f,
            "US / India Visa (2x2 in) • Pure White #FFFFFF • Consular Sharpness"
    ),

    VISA_SCHENGEN(
            "Schengen Visa",
            600,
            771, // 35x45mm ratio (approx 7:9)
            0.778f,
            new Color(244, 244, 244), // Light Off-White #F4F4F4 per Schengen rules
            0.75f,
            0.20f,
            1.12f, // Strict consular high contrast
            0.0f,
            "Schengen Visa (35x45mm) • Light Gray #F4F4F4 • High Contrast"
    ),

    VISA_UK(
            "UK Visa",
            600,
            771, // 35x45mm
            0.778f,
            new Color(240, 240, 240), // Light Cream / Light Gray #F0F0F0
            0.75f,
            0.20f,
            1.10f,
            0.0f,
            "UK Visa (35x45mm) • Light Cream #F0F0F0 • Biometric Spec"
    ),

    COMPANY_ID(
            "Company ID Badge",
            600,
            800, // Standard 3:4 ID badge ratio
            0.75f,
            new Color(232, 232, 232), // Corporate Light Gray #E8E8E8 (visually distinct from white)
            0.58f, // Shoulders-up, wider natural frame
            0.35f,
            1.06f,
            0.0f,
            "Standard ID Badge (3:4) • Light Gray #E8E8E8 • Shoulders-Up Crop"
    ),

    COLLEGE_ID(
            "College / Student ID",
            640,
            800, // 4:5 ratio
            0.80f,
            new Color(235, 243, 250), // Soft Light-Blue Tint #EBF3FA (visually distinct from company gray & white)
            0.60f, // Natural campus framing
            0.33f,
            1.03f,
            4.0f, // Slightly warmer, softer collegiate tone
            "Student ID (4:5) • Soft Light-Blue #EBF3FA • Natural Framing"
    );

    private final String displayName;
    private final int width;
    private final int height;
    private final float aspectRatio;
    private final Color backgroundColor;
    private final float faceHeightRatio;
    private final float topMarginRatio;
    private final float contrastMultiplier;
    private final float warmthOffset;
    private final String specLabel;

    PhotoSpec(String displayName, int width, int height, float aspectRatio, Color backgroundColor,
              float faceHeightRatio, float topMarginRatio, float contrastMultiplier, float warmthOffset,
              String specLabel) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.aspectRatio = aspectRatio;
        this.backgroundColor = backgroundColor;
        this.faceHeightRatio = faceHeightRatio;
        this.topMarginRatio = topMarginRatio;
        this.contrastMultiplier = contrastMultiplier;
        this.warmthOffset = warmthOffset;
        this.specLabel = specLabel;
    }

    /**
     * Resolve the exact PhotoSpec from photoType and optional country selection
     */
    public static PhotoSpec resolve(String photoType, String country) {
        if (photoType == null || photoType.isBlank()) {
            return PASSPORT;
        }

        String typeUpper = photoType.toUpperCase();
        String countryUpper = (country != null) ? country.toUpperCase() : "US";

        if ("VISA".equals(typeUpper) || "VISA_APPLICATION".equals(typeUpper)) {
            if ("SCHENGEN".equals(countryUpper) || "EUROPE".equals(countryUpper) || "FRANCE".equals(countryUpper) || "GERMANY".equals(countryUpper)) {
                return VISA_SCHENGEN;
            } else if ("UK".equals(countryUpper) || "BRITAIN".equals(countryUpper)) {
                return VISA_UK;
            } else {
                return VISA_US;
            }
        }

        if ("COMPANY_ID".equals(typeUpper) || "COMPANY_ID_BADGE".equals(typeUpper)) {
            return COMPANY_ID;
        }

        if ("COLLEGE_ID".equals(typeUpper) || "COLLEGE_STUDENT_ID".equals(typeUpper) || "STUDENT_ID".equals(typeUpper)) {
            return COLLEGE_ID;
        }

        return PASSPORT;
    }

    public static PhotoSpec fromType(String photoType) {
        return resolve(photoType, "US");
    }
}
