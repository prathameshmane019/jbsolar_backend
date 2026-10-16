package com.prathamesh.jbsolar.api.dto;

import java.util.UUID;

import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.domain.UploadPurpose;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UploadUrlRequest(
        @NotNull UploadResourceType resourceType,
        @NotNull UUID resourceId,
        UploadPurpose purpose,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 100) String contentType,
        @Min(1) @Max(10485760) long fileSize) {
}
