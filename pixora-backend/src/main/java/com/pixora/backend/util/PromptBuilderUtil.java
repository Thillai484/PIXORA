package com.pixora.backend.util;

import com.pixora.backend.entity.Photo;

public class PromptBuilderUtil {

    public static final String NEGATIVE_PROMPT = 
            "cartoon, 3d render, anime, illustration, distorted eyes, bad anatomy, blurry, low quality, pixelated, " +
            "overexposed, oversaturated, deformed hands, plastic skin, watermark, text, signature";

    /**
     * Build targeted generation prompt based on photo purpose and customization options
     */
    public static String buildPrompt(Photo photo) {
        String mode = photo.getMode() != null ? photo.getMode().toUpperCase() : "OFFICIAL";
        String type = photo.getPhotoType() != null ? photo.getPhotoType().toUpperCase() : "RESUME";

        if ("OFFICIAL".equals(mode)) {
            return switch (type) {
                case "PASSPORT" ->
                        "Official biometric passport photograph of the person, direct frontal angle, neutral facial expression, clear open eyes, mouth closed, wearing crisp formal dark collared shirt, plain solid pure white background, shadowless studio passport lighting, high sharpness, 8k resolution, authentic photorealistic skin texture, ICAO compliance";
                case "VISA" ->
                        "Standard official consular visa headshot of the person, facing camera directly, relaxed neutral expression, plain bright solid white backdrop, formal business attire, balanced diffused studio lighting, ultra-sharp facial features, 8k photorealistic portrait";
                case "LINKEDIN" ->
                        "A high-trust professional LinkedIn profile portrait of the person, warm approachable smile, wearing a tailored business blazer, modern corporate glass office background with soft bokeh depth of field, warm volumetric rim lighting, 8k UHD, executive portrait photography";
                case "COMPANY_ID" ->
                        "Corporate enterprise employee badge photo of the person, direct frontal angle, confident friendly expression, crisp formal business attire, neutral smooth light gray studio backdrop, clear balanced lighting, sharp identification portrait";
                case "COLLEGE_ID" ->
                        "University student identity card photo of the person, natural gentle smile, clean smart casual collared shirt, smooth neutral light backdrop, high clarity, realistic natural lighting";
                case "RESUME" ->
                        "Executive corporate resume headshot of the person, wearing a tailored dark blazer, pleasant confident expression, subtle executive studio background with gentle vignette, masterclass studio key lighting, ultra-realistic corporate photography, 8k";
                default ->
                        "A professional executive headshot of the person, tailored business attire, elegant studio lighting, neutral aesthetic backdrop, 8k resolution, photorealistic masterpiece";
            };
        }

        // Professional mode with custom combinations
        String styleDesc = switch (photo.getStyle() != null ? photo.getStyle().toUpperCase() : "CORPORATE") {
            case "STUDIO" -> "high-end editorial studio portrait, dramatic softbox lighting, crisp depth of field";
            case "CREATIVE" -> "modern dynamic creative professional portrait, stylish aesthetic, subtle cinematic color grade";
            case "MINIMAL" -> "clean minimalist portrait, modern soft lighting, immaculate composition";
            case "CORPORATE" -> "prestigious executive corporate portrait, authoritative yet approachable, pristine commercial photography";
            default -> "masterclass professional portrait, studio quality lighting";
        };

        String clothingDesc = switch (photo.getClothing() != null ? photo.getClothing().toUpperCase() : "BLAZER") {
            case "SUIT" -> "a sharp tailored dark executive suit with a crisp white shirt and tie";
            case "FORMAL_SHIRT" -> "a crisp pressed formal button-down collared shirt";
            case "CASUAL_SMART" -> "smart casual professional clothing, modern tailored jacket";
            case "BLAZER" -> "a sophisticated modern tailored blazer with clean lapels";
            default -> "professional executive attire";
        };

        String backgroundDesc = switch (photo.getBackground() != null ? photo.getBackground().toUpperCase() : "OFFICE") {
            case "STUDIO" -> "a dark elegant studio gradient background with subtle rim illumination";
            case "SOLID_COLOR" -> "a clean solid neutral gray studio backdrop";
            case "OUTDOOR_BLUR" -> "a modern architectural urban terrace with blurred city bokeh";
            case "OFFICE" -> "a contemporary modern glass office interior with soft natural depth of field";
            default -> "a high-end studio background";
        };

        return String.format(
                "A stunning professional portrait of the person, wearing %s, %s, set against %s, volumetric studio lighting, authentic skin textures, sharp focus on eyes, 8k UHD, photorealistic masterpiece",
                clothingDesc, styleDesc, backgroundDesc
        );
    }
}
