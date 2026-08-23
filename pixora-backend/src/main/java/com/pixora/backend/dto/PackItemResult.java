package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackItemResult {
    private Long photoId;
    private String type;
    private String mode;
    private String generatedImageUrl;
    private String specLabel;
    private ComplianceResult complianceResult;
    private String status; // "DONE", "PROCESSING", "FAILED"
    private String errorMessage;
}
