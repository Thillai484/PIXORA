package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoGenerationResponse {
    private boolean success;
    private Long photoId;
    private Long requestId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private String message;
}
