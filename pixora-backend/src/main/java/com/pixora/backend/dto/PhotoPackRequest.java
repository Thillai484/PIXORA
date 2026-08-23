package com.pixora.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoPackRequest {
    @NotBlank(message = "Pack type is required (e.g. PROFESSIONAL_PACK, OFFICIAL_PACK, COMPLETE_PACK)")
    private String packType;
}
