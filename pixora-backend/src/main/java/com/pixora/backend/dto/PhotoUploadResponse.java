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
public class PhotoUploadResponse {
    private boolean success;
    private Long photoId;
    private String originalImageUrl;
    private String status;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
}
