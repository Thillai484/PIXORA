package com.pixora.backend.repository;

import com.pixora.backend.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Photo> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
