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
public class GeneratePackResponse {
    private boolean success;
    private String packId;
    private Long originalPhotoId;
    private int total;
    private String status; // "DONE", "PROCESSING", "PARTIAL_SUCCESS", "FAILED"
    private List<PackItemResult> results;
}
