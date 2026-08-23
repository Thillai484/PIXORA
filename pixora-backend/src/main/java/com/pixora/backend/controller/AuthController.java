package com.pixora.backend.controller;

import com.pixora.backend.dto.AuthSyncRequest;
import com.pixora.backend.dto.FirebaseUserPrincipal;
import com.pixora.backend.dto.UserResponse;
import com.pixora.backend.service.FirebaseAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;

    /**
     * Synchronize / Register user after Google sign-in
     */
    @PostMapping("/google")
    public ResponseEntity<?> syncGoogleUser(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestBody(required = false) AuthSyncRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        FirebaseUserPrincipal userPrincipal = principal;

        // If principal was not resolved by filter, attempt verifying from body/header
        if (userPrincipal == null) {
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            } else if (request != null && request.getIdToken() != null) {
                token = request.getIdToken();
            }

            if (token != null && !token.isEmpty()) {
                userPrincipal = firebaseAuthService.verifyToken(token);
            }
        }

        if (userPrincipal == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Authentication failed: No valid token found in Authorization header or body");
            error.put("errorCode", "AUTH_TOKEN_MISSING");
            return ResponseEntity.status(401).body(error);
        }

        UserResponse userResponse = firebaseAuthService.syncGoogleUser(userPrincipal);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Get profile of current logged-in user
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        if (principal == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "User is not authenticated");
            error.put("errorCode", "UNAUTHORIZED");
            return ResponseEntity.status(401).body(error);
        }

        UserResponse response = firebaseAuthService.syncGoogleUser(principal);
        return ResponseEntity.ok(response);
    }
}
