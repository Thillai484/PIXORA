package com.pixora.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixora.backend.config.PhotoSpec;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PhotoRequestRepository photoRequestRepository;
    private final StorageService storageService;
    private final FirebaseAuthService firebaseAuthService;
    private final AIService aiService;
    private final OfficialPhotoService officialPhotoService;
    private final ComplianceCheckService complianceCheckService;
    private final ObjectMapper objectMapper;

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
        String purpose = (request.getPhotoType() != null && !request.getPhotoType().isBlank())
                ? request.getPhotoType().toUpperCase()
                : "RESUME";
        String country = (request.getCountry() != null && !request.getCountry().isBlank())
                ? request.getCountry().toUpperCase()
                : "US";

        photo.setCountry(country);

        if ("OFFICIAL".equalsIgnoreCase(mode)) {
            boolean isBiometricDoc = "PASSPORT".equalsIgnoreCase(purpose) ||
                    "PASSPORT_PHOTO".equalsIgnoreCase(purpose) ||
                    "VISA".equalsIgnoreCase(purpose) ||
                    "VISA_APPLICATION".equalsIgnoreCase(purpose) ||
                    "COMPANY_ID".equalsIgnoreCase(purpose) ||
                    "COMPANY_ID_BADGE".equalsIgnoreCase(purpose) ||
                    "COLLEGE_ID".equalsIgnoreCase(purpose) ||
                    "COLLEGE_STUDENT_ID".equalsIgnoreCase(purpose);

            if (isBiometricDoc) {
                photo.setMode("OFFICIAL");
                photo.setPhotoType(purpose);
                applyNormalizedOfficialAttributes(photo, purpose, country);
            } else {
                photo.setMode("PROFESSIONAL");
                photo.setPhotoType(purpose);
                applyNormalizedOfficialAttributes(photo, purpose, country);
            }
        } else {
            photo.setMode("PROFESSIONAL");
            photo.setPhotoType("PROFESSIONAL_CUSTOM");
            photo.setStyle(request.getStyle() != null ? request.getStyle().toUpperCase() : "CORPORATE");
            photo.setClothing(request.getClothing() != null ? request.getClothing().toUpperCase() : "BLAZER");
            photo.setBackground(request.getBackground() != null ? request.getBackground().toUpperCase() : "OFFICE");
            photo.setSpecLabel("AI Studio Portrait • " + photo.getStyle() + " • " + photo.getClothing());
        }

        photo.setStatus("CONFIGURED");
        photo = photoRepository.save(photo);

        log.info("Photo {} customized successfully with mode: {}, type: {}, spec: {}",
                photo.getId(), photo.getMode(), photo.getPhotoType(), photo.getSpecLabel());

        return mapToResponse(photo);
    }

    /**
     * Start single photo generation pipeline
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

        CompletableFuture.runAsync(() -> executeGenerationPipeline(finalPhotoId, finalRequestId));

        log.info("Dispatched generation pipeline for photo ID {}", photoId);

        return PhotoGenerationResponse.builder()
                .success(true)
                .photoId(finalPhotoId)
                .requestId(finalRequestId)
                .status("PROCESSING")
                .message("Photo generation started")
                .build();
    }

    /**
     * Core execution pipeline: Professional (LightX AI) vs Official (Deterministic Document Engine)
     */
    public void executeGenerationPipeline(Long photoId, Long requestId) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        PhotoRequest request = photoRequestRepository.findById(requestId).orElse(null);

        if (photo == null || request == null) {
            log.error("Generation pipeline aborted: Photo or Request not found");
            return;
        }

        try {
            String generatedUrl;

            boolean isOfficialDocument = "OFFICIAL".equalsIgnoreCase(photo.getMode()) &&
                    ("PASSPORT".equalsIgnoreCase(photo.getPhotoType()) ||
                            "PASSPORT_PHOTO".equalsIgnoreCase(photo.getPhotoType()) ||
                            "VISA".equalsIgnoreCase(photo.getPhotoType()) ||
                            "VISA_APPLICATION".equalsIgnoreCase(photo.getPhotoType()) ||
                            "COLLEGE_ID".equalsIgnoreCase(photo.getPhotoType()) ||
                            "COLLEGE_STUDENT_ID".equalsIgnoreCase(photo.getPhotoType()) ||
                            "COMPANY_ID".equalsIgnoreCase(photo.getPhotoType()) ||
                            "COMPANY_ID_BADGE".equalsIgnoreCase(photo.getPhotoType()));

            if (isOfficialDocument) {
                // Official Document Mode: Deterministic background removal + solid color composite + ICAO framing (NO AI)
                log.info("Executing Official Document Pipeline for photo ID {} (Type: {})", photoId, photo.getPhotoType());
                generatedUrl = officialPhotoService.processOfficialPhoto(photo);
            } else {
                // Professional Mode: Execute LightX AI Headshot Generation with distinct preset template
                String styleOrPreset = photo.getPhotoType() != null && !photo.getPhotoType().isBlank()
                        ? photo.getPhotoType()
                        : photo.getStyle();

                log.info("Executing LightX AI Generation for photo ID {} with preset: {}", photoId, styleOrPreset);
                generatedUrl = aiService.generateProfessionalPhoto(
                        photo.getOriginalImageUrl(),
                        photo.getClothing(),
                        photo.getBackground(),
                        styleOrPreset
                );
            }

            photo.setGeneratedImageUrl(generatedUrl);
            photo.setStatus("DONE");

            // Evaluate post-generation compliance checklist
            try {
                byte[] generatedBytes = storageService.getFileBytes(generatedUrl);
                ComplianceResult complianceResult = complianceCheckService.evaluateCompliance(photo, generatedBytes);
                photo.setComplianceResult(objectMapper.writeValueAsString(complianceResult));
                log.info("Compliance evaluated for photo ID {}: Score = {}%, Status = {}",
                        photoId, complianceResult.getComplianceScore(), complianceResult.getOverallStatus());
            } catch (Exception ce) {
                log.warn("Compliance check warning for photo ID {}: {}", photoId, ce.getMessage());
            }

            photoRepository.save(photo);

            request.setStatus("COMPLETED");
            request.setCompletedAt(LocalDateTime.now());
            photoRequestRepository.save(request);

            log.info("Generation succeeded for photo ID {}. URL: {}", photoId, generatedUrl);

        } catch (Exception e) {
            log.error("Generation pipeline error for photo ID {}: {}", photoId, e.getMessage());

            photo.setStatus("FAILED");
            photoRepository.save(photo);

            request.setStatus("FAILED");
            request.setErrorMessage("AI generation is temporarily unavailable, please try again.");
            request.setCompletedAt(LocalDateTime.now());
            photoRequestRepository.save(request);
        }
    }

    /**
     * Generate Pack: Core "One Photo -> Multiple Photos" parallel generation
     */
    public GeneratePackResponse generatePack(FirebaseUserPrincipal principal, GeneratePackRequest request) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo originalPhoto = photoRepository.findByIdAndUserId(request.getPhotoId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + request.getPhotoId()));

        String packId = "pack_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String country = (request.getCountry() != null && !request.getCountry().isBlank()) ? request.getCountry().toUpperCase() : "US";

        List<Photo> createdPhotos = new ArrayList<>();
        List<PhotoRequest> createdRequests = new ArrayList<>();

        for (String type : request.getTypes()) {
            boolean isOfficial = "PASSPORT".equalsIgnoreCase(type) ||
                    "PASSPORT_PHOTO".equalsIgnoreCase(type) ||
                    "VISA".equalsIgnoreCase(type) ||
                    "VISA_APPLICATION".equalsIgnoreCase(type) ||
                    "COMPANY_ID".equalsIgnoreCase(type) ||
                    "COMPANY_ID_BADGE".equalsIgnoreCase(type) ||
                    "COLLEGE_ID".equalsIgnoreCase(type) ||
                    "COLLEGE_STUDENT_ID".equalsIgnoreCase(type);

            Photo p = Photo.builder()
                    .userId(user.getId())
                    .packId(packId)
                    .originalImageUrl(originalPhoto.getOriginalImageUrl())
                    .photoType(type)
                    .mode(isOfficial ? "OFFICIAL" : "PROFESSIONAL")
                    .country(country)
                    .status("PROCESSING")
                    .build();

            if (!isOfficial && request.getSharedOptions() != null) {
                p.setStyle(request.getSharedOptions().getStyle());
                p.setClothing(request.getSharedOptions().getClothing());
                p.setBackground(request.getSharedOptions().getBackground());
            }

            applyNormalizedOfficialAttributes(p, type, country);
            p = photoRepository.save(p);

            PhotoRequest req = PhotoRequest.builder()
                    .userId(user.getId())
                    .photoId(p.getId())
                    .requestType("PACK_GENERATION")
                    .status("PROCESSING")
                    .build();
            req = photoRequestRepository.save(req);

            createdPhotos.add(p);
            createdRequests.add(req);
        }

        log.info("Initialized Photo Pack {} with {} items for user {}", packId, createdPhotos.size(), user.getId());

        // Execute parallel generation across all types
        List<CompletableFuture<PackItemResult>> futures = new ArrayList<>();

        for (int i = 0; i < createdPhotos.size(); i++) {
            final Photo p = createdPhotos.get(i);
            final PhotoRequest req = createdRequests.get(i);

            CompletableFuture<PackItemResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String generatedUrl;
                    boolean isOfficial = "OFFICIAL".equalsIgnoreCase(p.getMode());

                    if (isOfficial) {
                        generatedUrl = officialPhotoService.processOfficialPhoto(p);
                    } else {
                        generatedUrl = aiService.generateProfessionalPhoto(
                                p.getOriginalImageUrl(),
                                p.getClothing(),
                                p.getBackground(),
                                p.getPhotoType()
                        );
                    }

                    p.setGeneratedImageUrl(generatedUrl);
                    p.setStatus("DONE");

                    // Evaluate compliance
                    ComplianceResult compResult = null;
                    try {
                        byte[] generatedBytes = storageService.getFileBytes(generatedUrl);
                        compResult = complianceCheckService.evaluateCompliance(p, generatedBytes);
                        p.setComplianceResult(objectMapper.writeValueAsString(compResult));
                    } catch (Exception ignored) {}

                    photoRepository.save(p);

                    req.setStatus("COMPLETED");
                    req.setCompletedAt(LocalDateTime.now());
                    photoRequestRepository.save(req);

                    return PackItemResult.builder()
                            .photoId(p.getId())
                            .type(p.getPhotoType())
                            .mode(p.getMode())
                            .generatedImageUrl(generatedUrl)
                            .specLabel(p.getSpecLabel())
                            .complianceResult(compResult)
                            .status("DONE")
                            .build();

                } catch (Exception e) {
                    log.error("Failed pack generation item for type {}: {}", p.getPhotoType(), e.getMessage());

                    p.setStatus("FAILED");
                    photoRepository.save(p);

                    req.setStatus("FAILED");
                    req.setErrorMessage(e.getMessage());
                    req.setCompletedAt(LocalDateTime.now());
                    photoRequestRepository.save(req);

                    return PackItemResult.builder()
                            .photoId(p.getId())
                            .type(p.getPhotoType())
                            .mode(p.getMode())
                            .specLabel(p.getSpecLabel())
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build();
                }
            });

            futures.add(future);
        }

        // Wait for all items to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<PackItemResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        boolean hasSuccess = results.stream().anyMatch(r -> "DONE".equalsIgnoreCase(r.getStatus()));
        boolean allSuccess = results.stream().allMatch(r -> "DONE".equalsIgnoreCase(r.getStatus()));

        String overallStatus = allSuccess ? "DONE" : (hasSuccess ? "PARTIAL_SUCCESS" : "FAILED");

        return GeneratePackResponse.builder()
                .success(hasSuccess)
                .packId(packId)
                .originalPhotoId(request.getPhotoId())
                .total(results.size())
                .status(overallStatus)
                .results(results)
                .build();
    }

    /**
     * Fetch all photos belonging to a pack
     */
    @Transactional(readOnly = true)
    public List<PhotoResponse> getPackPhotos(FirebaseUserPrincipal principal, String packId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);
        List<Photo> photos = photoRepository.findByPackIdAndUserIdOrderByCreatedAtAsc(packId, user.getId());

        return photos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Start a Photo Pack (legacy / background batch generation)
     */
    @Transactional
    public PhotoPackResponse startPhotoPack(FirebaseUserPrincipal principal, Long originalPhotoId, PhotoPackRequest request) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo originalPhoto = photoRepository.findByIdAndUserId(originalPhotoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + originalPhotoId));

        String packType = (request.getPackType() != null && !request.getPackType().isBlank())
                ? request.getPackType().toUpperCase()
                : "PROFESSIONAL_PACK";

        List<String> presets = switch (packType) {
            case "OFFICIAL_PACK", "OFFICIAL" -> List.of("PASSPORT", "VISA", "COLLEGE_ID");
            case "COMPLETE_PACK", "COMPLETE", "ALL" -> List.of("RESUME", "LINKEDIN", "PASSPORT", "VISA", "COMPANY_ID", "COLLEGE_ID");
            case "PROFESSIONAL_PACK", "PROFESSIONAL" -> List.of("RESUME", "LINKEDIN", "COMPANY_ID");
            default -> List.of("RESUME", "LINKEDIN", "PASSPORT");
        };

        String packId = "pack_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        List<Photo> createdPhotos = new ArrayList<>();
        List<Long> photoIds = new ArrayList<>();

        for (String preset : presets) {
            boolean isOfficial = "PASSPORT".equalsIgnoreCase(preset) ||
                    "PASSPORT_PHOTO".equalsIgnoreCase(preset) ||
                    "VISA".equalsIgnoreCase(preset) ||
                    "VISA_APPLICATION".equalsIgnoreCase(preset) ||
                    "COMPANY_ID".equalsIgnoreCase(preset) ||
                    "COMPANY_ID_BADGE".equalsIgnoreCase(preset) ||
                    "COLLEGE_ID".equalsIgnoreCase(preset) ||
                    "COLLEGE_STUDENT_ID".equalsIgnoreCase(preset);

            Photo packPhoto = Photo.builder()
                    .userId(user.getId())
                    .packId(packId)
                    .originalImageUrl(originalPhoto.getOriginalImageUrl())
                    .mode(isOfficial ? "OFFICIAL" : "PROFESSIONAL")
                    .photoType(preset)
                    .country("US")
                    .status("PROCESSING")
                    .build();

            applyNormalizedOfficialAttributes(packPhoto, preset, "US");
            packPhoto = photoRepository.save(packPhoto);

            PhotoRequest photoRequest = PhotoRequest.builder()
                    .userId(user.getId())
                    .photoId(packPhoto.getId())
                    .requestType("PACK_GENERATION")
                    .status("PROCESSING")
                    .build();
            photoRequest = photoRequestRepository.save(photoRequest);

            createdPhotos.add(packPhoto);
            photoIds.add(packPhoto.getId());

            final Long pId = packPhoto.getId();
            final Long rId = photoRequest.getId();
            CompletableFuture.runAsync(() -> executeGenerationPipeline(pId, rId));
        }

        log.info("Dispatched Photo Pack ({}) with {} photos for user {}", packType, createdPhotos.size(), user.getId());

        List<PhotoResponse> responses = createdPhotos.stream().map(this::mapToResponse).collect(Collectors.toList());

        return PhotoPackResponse.builder()
                .success(true)
                .packType(packType)
                .originalPhotoId(originalPhotoId)
                .totalPhotos(createdPhotos.size())
                .generatedPhotoIds(photoIds)
                .photos(responses)
                .status("PROCESSING")
                .message("Started batch generation for " + createdPhotos.size() + " photos")
                .build();
    }

    /**
     * Bundle multiple generated photos into a ZIP archive by list of IDs
     */
    @Transactional(readOnly = true)
    public byte[] downloadPackZip(FirebaseUserPrincipal principal, List<Long> photoIds) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Long id : photoIds) {
                Photo photo = photoRepository.findByIdAndUserId(id, user.getId()).orElse(null);
                if (photo != null) {
                    String targetUrl = photo.getGeneratedImageUrl() != null ? photo.getGeneratedImageUrl() : photo.getOriginalImageUrl();
                    byte[] bytes = storageService.getFileBytes(targetUrl);

                    if (bytes != null && bytes.length > 0) {
                        String entryName = String.format("pixora-%s-%d.jpg",
                                photo.getPhotoType() != null ? photo.getPhotoType().toLowerCase() : "portrait",
                                photo.getId());
                        ZipEntry entry = new ZipEntry(entryName);
                        zos.putNextEntry(entry);
                        zos.write(bytes);
                        zos.closeEntry();
                    }
                }
            }

            zos.finish();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to create ZIP package: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Bundle all photos in a pack into a ZIP archive by packId
     */
    @Transactional(readOnly = true)
    public byte[] downloadPackZipByPackId(FirebaseUserPrincipal principal, String packId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);
        List<Photo> photos = photoRepository.findByPackIdAndUserIdOrderByCreatedAtAsc(packId, user.getId());

        List<Long> ids = photos.stream().map(Photo::getId).collect(Collectors.toList());
        return downloadPackZip(principal, ids);
    }

    /**
     * Check live status of photo generation
     */
    @Transactional(readOnly = true)
    public PhotoStatusResponse getPhotoStatus(FirebaseUserPrincipal principal, Long photoId) {
        UserResponse user = firebaseAuthService.syncGoogleUser(principal);

        Photo photo = photoRepository.findByIdAndUserId(photoId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + photoId));

        ComplianceResult compResult = parseComplianceResult(photo.getComplianceResult());

        return PhotoStatusResponse.builder()
                .success(true)
                .photoId(photo.getId())
                .packId(photo.getPackId())
                .status(photo.getStatus())
                .originalImageUrl(photo.getOriginalImageUrl())
                .generatedImageUrl(photo.getGeneratedImageUrl())
                .mode(photo.getMode())
                .photoType(photo.getPhotoType())
                .country(photo.getCountry())
                .style(photo.getStyle())
                .clothing(photo.getClothing())
                .background(photo.getBackground())
                .specLabel(photo.getSpecLabel())
                .complianceResult(compResult)
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

        photoRequestRepository.deleteByPhotoId(photo.getId());

        if (photo.getOriginalImageUrl() != null) {
            storageService.deleteFile(photo.getOriginalImageUrl());
        }
        if (photo.getGeneratedImageUrl() != null) {
            storageService.deleteFile(photo.getGeneratedImageUrl());
        }

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

    private void applyNormalizedOfficialAttributes(Photo photo, String purpose, String country) {
        PhotoSpec spec = PhotoSpec.resolve(purpose, country);

        String upper = purpose != null ? purpose.toUpperCase() : "RESUME";

        if ("PASSPORT".equals(upper) || "PASSPORT_PHOTO".equals(upper)) {
            photo.setStyle("STUDIO");
            photo.setClothing("FORMAL_SHIRT");
            photo.setBackground("WHITE");
            photo.setSpecLabel(spec.getSpecLabel());
        } else if ("VISA".equals(upper) || "VISA_APPLICATION".equals(upper)) {
            photo.setStyle("STUDIO");
            photo.setClothing("FORMAL_SHIRT");
            photo.setBackground(spec == PhotoSpec.VISA_SCHENGEN ? "LIGHT_GRAY" : "WHITE");
            photo.setSpecLabel(spec.getSpecLabel());
        } else if ("COMPANY_ID".equals(upper) || "COMPANY_ID_BADGE".equals(upper)) {
            photo.setStyle("STUDIO");
            photo.setClothing("FORMAL_SHIRT");
            photo.setBackground("LIGHT_GRAY");
            photo.setSpecLabel(spec.getSpecLabel());
        } else if ("COLLEGE_ID".equals(upper) || "COLLEGE_STUDENT_ID".equals(upper)) {
            photo.setStyle("STUDIO");
            photo.setClothing("FORMAL_SHIRT");
            photo.setBackground("LIGHT_BLUE");
            photo.setSpecLabel(spec.getSpecLabel());
        } else if ("LINKEDIN".equals(upper)) {
            photo.setStyle("CORPORATE");
            photo.setClothing("BLAZER");
            photo.setBackground("OFFICE");
            photo.setSpecLabel("Executive Networking Headshot • Soft Office Bokeh");
        } else {
            photo.setStyle("CORPORATE");
            photo.setClothing("BLAZER");
            photo.setBackground("STUDIO");
            photo.setSpecLabel("Corporate Resume Headshot • Dark Blazer & Studio Lighting");
        }
    }

    public PhotoResponse mapToResponse(Photo photo) {
        ComplianceResult compResult = parseComplianceResult(photo.getComplianceResult());

        return PhotoResponse.builder()
                .id(photo.getId())
                .userId(photo.getUserId())
                .packId(photo.getPackId())
                .originalImageUrl(photo.getOriginalImageUrl())
                .generatedImageUrl(photo.getGeneratedImageUrl())
                .photoType(photo.getPhotoType())
                .mode(photo.getMode())
                .country(photo.getCountry())
                .style(photo.getStyle())
                .clothing(photo.getClothing())
                .background(photo.getBackground())
                .specLabel(photo.getSpecLabel())
                .complianceResult(compResult)
                .status(photo.getStatus())
                .createdAt(photo.getCreatedAt())
                .updatedAt(photo.getUpdatedAt())
                .build();
    }

    private ComplianceResult parseComplianceResult(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ComplianceResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse complianceResult JSON: {}", e.getMessage());
            return null;
        }
    }
}
