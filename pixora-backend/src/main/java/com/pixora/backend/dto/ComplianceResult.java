package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceResult {
    private String overallStatus; // "PASS", "NEEDS_REVIEW", "FAIL"
    private int complianceScore;  // e.g. 100
    private String summary;       // e.g. "Fully compliant with 2x2 in ICAO biometric passport standard"
    private List<ComplianceCheckItem> checks;
}
