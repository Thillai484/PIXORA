package com.pixora.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "photo_requests", indexes = {
        @Index(name = "idx_requests_user_id", columnList = "user_id"),
        @Index(name = "idx_requests_photo_id", columnList = "photo_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    @Column(name = "request_type", length = 64)
    @Builder.Default
    private String requestType = "SINGLE_PHOTO";

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PROCESSING"; // PROCESSING, COMPLETED, FAILED

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
