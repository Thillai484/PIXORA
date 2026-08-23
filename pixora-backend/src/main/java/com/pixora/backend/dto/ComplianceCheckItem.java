package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckItem {
    private String label;      // e.g. "Background Purity", "Face Height Ratio", "Aspect Ratio & Dimensions", "Biometric Centering"
    private String status;     // "PASS", "WARNING", "FAIL"
    private String detail;     // e.g. "Pure white #FFFFFF within ±2 RGB variance"
}
