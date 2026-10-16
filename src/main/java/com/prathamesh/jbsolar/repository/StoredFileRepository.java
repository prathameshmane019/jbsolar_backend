package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.prathamesh.jbsolar.domain.StoredFile;
import java.time.Instant;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndCreatedById(UUID id, UUID agentId);
    List<StoredFile> findAllByResourceTypeAndResourceIdAndCreatedByIdAndUploadedAtIsNotNullOrderByCreatedAtDesc(
            com.prathamesh.jbsolar.domain.UploadResourceType resourceType, UUID resourceId, UUID agentId);
    List<StoredFile> findAllByUploadedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
            Instant cutoff, Pageable pageable);
}
