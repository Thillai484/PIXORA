package com.pixora.backend.controller;

import com.pixora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final StorageService storageService;

    /**
     * Test endpoint for confirming Supabase Storage uploads
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> testUpload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "File cannot be empty");
                error.put("errorCode", "EMPTY_FILE");
                return ResponseEntity.badRequest().body(error);
            }

            String publicUrl = storageService.uploadOriginalPhoto(
                    1L,
                    file.getOriginalFilename(),
                    file.getBytes(),
                    file.getContentType()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("publicUrl", publicUrl);
            response.put("filename", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("contentType", file.getContentType());
            response.put("supabaseConfigured", storageService.isConfigured());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Test upload failed: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Upload failed: " + e.getMessage());
            error.put("errorCode", "STORAGE_UPLOAD_ERROR");
            return ResponseEntity.status(500).body(error);
        }
    }
}
