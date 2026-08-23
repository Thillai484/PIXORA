package com.pixora.backend.controller;

import com.pixora.backend.dto.FirebaseUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    /**
     * Protected endpoint to verify authentication and retrieve user status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getPhotoServiceStatus(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Authenticated photo service access verified");
        response.put("userId", principal != null ? principal.getId() : null);
        response.put("userEmail", principal != null ? principal.getEmail() : null);
        return ResponseEntity.ok(response);
    }
}
