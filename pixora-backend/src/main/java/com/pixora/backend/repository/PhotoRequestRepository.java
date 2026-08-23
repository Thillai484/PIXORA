package com.pixora.backend.repository;

import com.pixora.backend.entity.PhotoRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhotoRequestRepository extends JpaRepository<PhotoRequest, Long> {

    List<PhotoRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PhotoRequest> findByPhotoId(Long photoId);

    void deleteByPhotoId(Long photoId);
}
