package com.pixora.backend.util;

import com.pixora.backend.entity.Photo;

public class PromptBuilderUtil {

    public static final String NEGATIVE_PROMPT =
            "cartoon, 3d render, anime, illustration, distorted eyes, bad anatomy, blurry, low quality, pixelated, " +
                    "overexposed, oversaturated, deformed hands, plastic skin, watermark, text, signature";

    /**
     * Build distinct, targeted generation prompts per photo preset
     */
    public static String buildPrompt(Photo photo) {
        String type = photo.getPhotoType() != null ? photo.getPhotoType().toUpperCase() : "";

        // 1. Specific Document & Professional Presets
        switch (type) {
            case "PASSPORT":
                return "Official biometric passport photograph of the person, direct frontal angle, neutral facial expression, clear open eyes, mouth closed, wearing crisp formal dark collared shirt, plain solid pure white background, shadowless studio passport lighting, high sharpness, 8k resolution, authentic photorealistic skin texture, ICAO compliance";

            case "VISA":
                return "Standard official consular visa headshot of the person, facing camera directly, relaxed neutral expression, plain bright solid white backdrop, formal business attire, balanced diffused studio lighting, ultra-sharp facial features, 8k photorealistic portrait";

            case "RESUME":
                return "professional corporate headshot, dark navy blazer over collared shirt, plain studio gray background, soft even studio lighting, direct eye contact, neutral confident expression, sharp focus, high resolution corporate portrait photography";

            case "LINKEDIN":
                return "approachable professional headshot, business casual blazer, softly blurred modern office background, warm natural lighting, slight friendly smile, shoulders angled slightly, networking profile photo style";

            case "JOB_APPLICATION":
            case "CAREER":
                return "clean professional headshot, business formal attire, solid light blue or gray background, bright even lighting, formal neutral expression, passport-adjacent but softer styling";

            case "COMPANY_ID":
                return "corporate employee ID badge photo, formal business attire, light gray studio background, sharp focus, neutral direct lighting";

            case "COLLEGE_ID":
                return "university student identity card portrait, smart casual collared shirt, smooth neutral light backdrop, high clarity, realistic natural lighting";
        }

        // 2. Custom Professional Combinations
        String styleDesc = switch (photo.getStyle() != null ? photo.getStyle().toUpperCase() : "CORPORATE") {
            case "STUDIO" -> "high-end editorial studio portrait, dramatic softbox lighting, crisp depth of field";
            case "CREATIVE" -> "modern dynamic creative professional portrait, stylish aesthetic, subtle cinematic lighting";
            case "MINIMAL" -> "clean minimalist portrait, modern soft lighting, immaculate composition";
            case "CORPORATE" -> "prestigious executive corporate portrait, authoritative yet approachable, commercial headshot style";
            default -> "masterclass professional portrait, studio quality lighting";
        };

        String clothingDesc = switch (photo.getClothing() != null ? photo.getClothing().toUpperCase() : "BLAZER") {
            case "SUIT" -> "a sharp tailored dark executive suit with a crisp white shirt and tie";
            case "FORMAL_SHIRT" -> "a crisp pressed formal button-down collared shirt";
            case "CASUAL_SMART" -> "smart casual professional clothing, modern tailored jacket";
            case "BLAZER" -> "a sophisticated dark navy tailored blazer with clean lapels";
            default -> "professional tailored business attire";
        };

        String backgroundDesc = switch (photo.getBackground() != null ? photo.getBackground().toUpperCase() : "OFFICE") {
            case "STUDIO" -> "a dark elegant studio gradient background with soft illumination";
            case "SOLID_COLOR" -> "a clean solid neutral gray studio backdrop";
            case "OUTDOOR_BLUR" -> "a modern architectural urban terrace with blurred city bokeh";
            case "OFFICE" -> "a contemporary modern glass office interior with soft natural depth of field";
            default -> "a softly blurred professional office background";
        };

        return String.format(
                "A stunning professional portrait of the person, wearing %s, %s, set against %s, studio portrait lighting, natural authentic skin, sharp focus, high resolution corporate photography",
                clothingDesc, styleDesc, backgroundDesc
        );
    }
}
