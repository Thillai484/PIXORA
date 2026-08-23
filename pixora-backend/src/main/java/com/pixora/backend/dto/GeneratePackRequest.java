package com.pixora.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePackRequest {

    @NotNull(message = "photoId is required")
    private Long photoId;

    @NotEmpty(message = "At least one photo type must be selected")
    private List<String> types; // ["RESUME", "LINKEDIN", "PASSPORT", "COMPANY_ID", "COLLEGE_ID", "VISA"]

    private String country; // For VISA preset (US, SCHENGEN, UK, INDIA)

    private SharedOptions sharedOptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedOptions {
        private String style;      // CORPORATE, STUDIO, CREATIVE, MINIMAL
        private String clothing;   // BLAZER, SUIT, FORMAL_SHIRT, CASUAL_SMART
        private String background; // OFFICE, STUDIO, SOLID_COLOR, OUTDOOR_BLUR
    }
}
