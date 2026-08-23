package com.pixora.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "photos", indexes = {
        @Index(name = "idx_photos_user_id", columnList = "user_id"),
        @Index(name = "idx_photos_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_image_url", nullable = false, length = 2048)
    private String originalImageUrl;

    @Column(name = "generated_image_url", length = 2048)
    private String generatedImageUrl;

    @Column(name = "photo_type", length = 64)
    private String photoType;

    @Column(length = 32)
    private String mode; // OFFICIAL, PROFESSIONAL

    @Column(length = 64)
    private String country; // US, SCHENGEN, UK, INDIA, GENERAL

    @Column(length = 64)
    private String style;

    @Column(length = 64)
    private String clothing;

    @Column(length = 64)
    private String background;

    @Column(length = 256)
    private String specLabel;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "UPLOADED"; // UPLOADED, PROCESSING, DONE, FAILED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
