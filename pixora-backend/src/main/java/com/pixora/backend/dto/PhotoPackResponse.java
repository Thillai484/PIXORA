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
public class PhotoPackResponse {
    private boolean success;
    private String packType;
    private Long originalPhotoId;
    private int totalPhotos;
    private List<Long> generatedPhotoIds;
    private List<PhotoResponse> photos;
    private String status; // PROCESSING, COMPLETED
    private String message;
}
