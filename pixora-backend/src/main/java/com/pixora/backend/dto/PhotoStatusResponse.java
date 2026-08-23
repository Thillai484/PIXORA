package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoStatusResponse {
    private boolean success;
    private Long photoId;
    private String status; // UPLOADED, CONFIGURED, PROCESSING, DONE, FAILED
    private String originalImageUrl;
    private String generatedImageUrl;
    private String mode;
    private String photoType;
    private String country;
    private String style;
    private String clothing;
    private String background;
    private String specLabel;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
