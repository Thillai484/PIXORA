package com.pixora.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizePhotoRequest {

    @NotNull(message = "photoId is required")
    private Long photoId;

    private String mode; // OFFICIAL, PROFESSIONAL

    private String photoType; // RESUME, LINKEDIN, PASSPORT, VISA, COMPANY_ID, COLLEGE_ID

    private String country; // US, SCHENGEN, UK, INDIA, GENERAL

    private String style; // CORPORATE, CREATIVE, STUDIO, MINIMAL

    private String clothing; // SUIT, BLAZER, FORMAL_SHIRT, CASUAL_SMART

    private String background; // OFFICE, STUDIO, SOLID_COLOR, OUTDOOR_BLUR, WHITE, LIGHT_GRAY
}
