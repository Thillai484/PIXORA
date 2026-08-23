package com.pixora.backend.service;

import com.pixora.backend.dto.*;
import com.pixora.backend.entity.Photo;
import com.pixora.backend.entity.PhotoRequest;
import com.pixora.backend.repository.PhotoRepository;
import com.pixora.backend.repository.PhotoRequestRepository;
import com.pixora.backend.util.ImageValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PhotoRequestRepository photoRequestRepository;
    private final StorageService storageService;
    private final FirebaseAuthService firebaseAuthService;
    private final AIService aiService;

    /**
     * Process, validate, and store a user-uploaded photo
     */
    @Transactional
    public PhotoUploadResponse uploadPhoto(FirebaseUserPrincipal principal, MultipartFile file) throws IOException {
        BufferedImage image = ImageValidationUtil.validateImage(file);
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        String publicUrl = storageService.uploadOriginalPhoto(
                user.getId(),
                file.getOriginalFilename(),
                file.getBytes(),
                file.getContentType()
        );

        Photo photo = Photo.builder()
                .userId(user.getId())
                .originalImageUrl(publicUrl)
                .status("UPLOADED")
                .build();

        photo = photoRepository.save(photo);

        log.info("Photo uploaded successfully with ID {} for user {}", photo.getId(), user.getId());

        return PhotoUploadResponse.builder()
                .success(true)
                .photoId(photo.getId())
                .originalImageUrl(photo.getOriginalImageUrl())
                .status(photo.getStatus())
                .width(image.getWidth())
                .height(image.getHeight())
                .createdAt(photo.getCreatedAt())
                .build();
    }

    /**
     * Update customization options for a user photo
     */
    @Transactional
    public PhotoResponse customizePhoto(FirebaseUserPrincipal principal, CustomizePhotoRequest request) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo photo = photoRepository.findByIdAndUserId(request.getPhotoId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found or does not belong to user with id: " + request.getPhotoId()));

        String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode().toUpperCase() : "OFFICIAL";

        if ("OFFICIAL".equalsIgnoreCase(mode)) {
            String purpose = (request.getPhotoType() != null && !request.getPhotoType().isBlank())
                    ? request.getPhotoType().toUpperCase()
                    : "RESUME";

            photo.setMode("OFFICIAL");
            photo.setPhotoType(purpose);

            // Automatically normalize preset attributes for official formats
            switch (purpose) {
                case "PASSPORT":
                case "VISA":
                    photo.setStyle("STUDIO");
                    photo.setClothing("FORMAL_SHIRT");
                    photo.setBackground("WHITE");
                    break;
                case "COMPANY_ID":
                case "COLLEGE_ID":
                    photo.setStyle("STUDIO");
                    photo.setClothing("FORMAL_SHIRT");
                    photo.setBackground("LIGHT_GRAY");
                    break;
                case "LINKEDIN":
                case "RESUME":
                default:
                    photo.setStyle("CORPORATE");
                    photo.setClothing("BLAZER");
                    photo.setBackground("STUDIO");
                    break;
            }
        } else {
            // Professional mode with custom choices
            photo.setMode("PROFESSIONAL");
            photo.setPhotoType("PROFESSIONAL_CUSTOM");
            photo.setStyle(request.getStyle() != null ? request.getStyle().toUpperCase() : "CORPORATE");
            photo.setClothing(request.getClothing() != null ? request.getClothing().toUpperCase() : "BLAZER");
            photo.setBackground(request.getBackground() != null ? request.getBackground().toUpperCase() : "OFFICE");
        }

        photo.setStatus("CONFIGURED");
        photo = photoRepository.save(photo);

        log.info("Photo {} customized successfully with mode: {}, type: {}", photo.getId(), photo.getMode(), photo.getPhotoType());

        return mapToResponse(photo);
    }

    /**
     * Start the AI photo generation pipeline asynchronously
     */
    @Transactional
    public PhotoGenerationResponse startPhotoGeneration(FirebaseUserPrincipal principal, Long photoId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo photo = photoRepository.findByIdAndUserId(photoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found or does not belong to user with id: " + photoId));

        PhotoRequest photoRequest = PhotoRequest.builder()
                .userId(user.getId())
                .photoId(photo.getId())
                .requestType("SINGLE_PHOTO")
                .status("PROCESSING")
                .build();

        photoRequest = photoRequestRepository.save(photoRequest);

        photo.setStatus("PROCESSING");
        photoRepository.save(photo);

        final Long finalPhotoId = photo.getId();
        final Long finalRequestId = photoRequest.getId();

        CompletableFuture.runAsync(() -> aiService.processGeneration(finalPhotoId, finalRequestId));

        log.info("Dispatched async AI generation for photo ID {}", photoId);

        return PhotoGenerationResponse.builder()
                .success(true)
                .photoId(finalPhotoId)
                .requestId(finalRequestId)
                .status("PROCESSING")
                .message("Photo generation started")
                .build();
    }

    /**
     * Check live status of photo generation
     */
    @Transactional(readOnly = true)
    public PhotoStatusResponse getPhotoStatus(FirebaseUserPrincipal principal, Long photoId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo photo = photoRepository.findByIdAndUserId(photoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + photoId));

        return PhotoStatusResponse.builder()
                .success(true)
                .photoId(photo.getId())
                .status(photo.getStatus())
                .originalImageUrl(photo.getOriginalImageUrl())
                .generatedImageUrl(photo.getGeneratedImageUrl())
                .mode(photo.getMode())
                .photoType(photo.getPhotoType())
                .style(photo.getStyle())
                .clothing(photo.getClothing())
                .background(photo.getBackground())
                .createdAt(photo.getCreatedAt())
                .updatedAt(photo.getUpdatedAt())
                .build();
    }

    /**
     * Retrieve all photos belonging to the authenticated user
     */
    @Transactional(readOnly = true)
    public List<PhotoResponse> getUserPhotos(FirebaseUserPrincipal principal) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);
        List<Photo> photos = photoRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return photos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete a photo and its associated files
     */
    @Transactional
    public void deletePhoto(FirebaseUserPrincipal principal, Long photoId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo photo = photoRepository.findByIdAndUserId(photoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found or does not belong to user with id: " + photoId));

        // 1. Delete associated PhotoRequests
        photoRequestRepository.deleteByPhotoId(photo.getId());

        // 2. Delete storage files
        if (photo.getOriginalImageUrl() != null) {
            storageService.deleteFile(photo.getOriginalImageUrl());
        }
        if (photo.getGeneratedImageUrl() != null) {
            storageService.deleteFile(photo.getGeneratedImageUrl());
        }

        // 3. Delete Photo entity
        photoRepository.delete(photo);

        log.info("Photo {} deleted successfully for user {}", photoId, user.getId());
    }

    /**
     * Download generated image bytes for high-res download
     */
    @Transactional(readOnly = true)
    public byte[] downloadPhoto(FirebaseUserPrincipal principal, Long photoId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);
        Photo photo = photoRepository.findByIdAndUserId(photoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + photoId));

        String targetUrl = photo.getGeneratedImageUrl() != null ? photo.getGeneratedImageUrl() : photo.getOriginalImageUrl();
        return storageService.getFileBytes(targetUrl);
    }

    /**
     * Fetch photo details by ID and verified User ID
     */
    @Transactional(readOnly = true)
    public PhotoResponse getPhotoById(Long id, Long userId) {
        Photo photo = photoRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + id));

        return mapToResponse(photo);
    }

    public PhotoResponse mapToResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .userId(photo.getUserId())
                .originalImageUrl(photo.getOriginalImageUrl())
                .generatedImageUrl(photo.getGeneratedImageUrl())
                .photoType(photo.getPhotoType())
                .mode(photo.getMode())
                .style(photo.getStyle())
                .clothing(photo.getClothing())
                .background(photo.getBackground())
                .status(photo.getStatus())
                .createdAt(photo.getCreatedAt())
                .updatedAt(photo.getUpdatedAt())
                .build();
    }
}
