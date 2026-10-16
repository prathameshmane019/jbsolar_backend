package com.prathamesh.jbsolar.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.domain.UploadPurpose;

public record FileMetadataResponse(UUID id, UploadResourceType resourceType, UUID resourceId,
        UploadPurpose purpose, String originalFilename, String contentType, long fileSize, Instant uploadedAt) {
}
