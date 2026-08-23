package com.pixora.backend.controller;

import com.pixora.backend.dto.*;
import com.pixora.backend.service.PhotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    /**
     * Upload an original user photo for processing
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        PhotoUploadResponse response = photoService.uploadPhoto(principal, file);
        return ResponseEntity.ok(response);
    }

    /**
     * Customize a photo with mode and style options
     */
    @PostMapping("/customize")
    public ResponseEntity<PhotoResponse> customizePhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody CustomizePhotoRequest request
    ) {
        PhotoResponse response = photoService.customizePhoto(principal, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Alternative customize endpoint matching /photos/{id}/customize
     */
    @PostMapping("/{id}/customize")
    public ResponseEntity<PhotoResponse> customizePhotoById(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody CustomizePhotoRequest request
    ) {
        request.setPhotoId(id);
        PhotoResponse response = photoService.customizePhoto(principal, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Start AI photo generation (POST /api/photos/generate with JSON body)
     */
    @PostMapping("/generate")
    public ResponseEntity<PhotoGenerationResponse> generatePhotoByBody(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        Long photoId = null;
        if (body.containsKey("photoId")) {
            photoId = Long.valueOf(body.get("photoId").toString());
        } else if (body.containsKey("id")) {
            photoId = Long.valueOf(body.get("id").toString());
        }
        if (photoId == null) {
            throw new IllegalArgumentException("photoId is required in request body");
        }
        PhotoGenerationResponse response = photoService.startPhotoGeneration(principal, photoId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Start AI photo generation by ID path parameter
     */
    @PostMapping("/{id}/generate")
    public ResponseEntity<PhotoGenerationResponse> generatePhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        PhotoGenerationResponse response = photoService.startPhotoGeneration(principal, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Core: One Photo -> Multiple Photos parallel pack generation (POST /api/photos/generate-pack)
     */
    @PostMapping("/generate-pack")
    public ResponseEntity<GeneratePackResponse> generatePack(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody GeneratePackRequest request
    ) {
        GeneratePackResponse response = photoService.generatePack(principal, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch all photos belonging to a pack
     */
    @GetMapping("/pack/{packId}")
    public ResponseEntity<List<PhotoResponse>> getPackPhotos(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable String packId
    ) {
        List<PhotoResponse> photos = photoService.getPackPhotos(principal, packId);
        return ResponseEntity.ok(photos);
    }

    /**
     * Download entire pack as a ZIP archive by packId
     */
    @GetMapping("/pack/{packId}/zip")
    public ResponseEntity<byte[]> downloadPackZipByPackId(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable String packId
    ) {
        byte[] zipBytes = photoService.downloadPackZipByPackId(principal, packId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pixora-" + packId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    /**
     * Legacy Photo Pack generation
     */
    @PostMapping("/{id}/pack")
    public ResponseEntity<PhotoPackResponse> generatePhotoPack(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PhotoPackRequest request
    ) {
        PhotoPackResponse response = photoService.startPhotoPack(principal, id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Download generated pack bundled into a ZIP archive by query ids
     */
    @GetMapping("/pack/zip")
    public ResponseEntity<byte[]> downloadPackZip(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestParam("ids") List<Long> ids
    ) {
        byte[] zipBytes = photoService.downloadPackZip(principal, ids);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pixora-photo-pack.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    /**
     * Check live progress status of photo generation
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<PhotoStatusResponse> getPhotoStatus(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        PhotoStatusResponse response = photoService.getPhotoStatus(principal, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Download generated photo as an image attachment
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadPhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        byte[] imageBytes = photoService.downloadPhoto(principal, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pixora-portrait-" + id + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    /**
     * Retrieve all photos belonging to the authenticated user
     */
    @GetMapping
    public ResponseEntity<List<PhotoResponse>> getUserPhotos(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        List<PhotoResponse> photos = photoService.getUserPhotos(principal);
        return ResponseEntity.ok(photos);
    }

    /**
     * Alias for /api/photos/me
     */
    @GetMapping("/me")
    public ResponseEntity<List<PhotoResponse>> getMyPhotos(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        List<PhotoResponse> photos = photoService.getUserPhotos(principal);
        return ResponseEntity.ok(photos);
    }

    /**
     * Get details of a specific photo by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PhotoResponse> getPhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        PhotoResponse response = photoService.getPhotoById(id, principal.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a photo by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePhoto(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        photoService.deletePhoto(principal, id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Photo deleted successfully");
        response.put("photoId", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Protected health/status check
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
