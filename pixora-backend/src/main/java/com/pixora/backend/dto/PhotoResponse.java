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
public class PhotoResponse {
    private Long id;
    private Long userId;
    private String originalImageUrl;
    private String generatedImageUrl;
    private String photoType;
    private String mode;
    private String style;
    private String clothing;
    private String background;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
