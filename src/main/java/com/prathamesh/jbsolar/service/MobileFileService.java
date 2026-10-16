package com.prathamesh.jbsolar.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.FileMetadataResponse;
import com.prathamesh.jbsolar.api.dto.UploadUrlRequest;
import com.prathamesh.jbsolar.api.dto.UploadUrlResponse;
import com.prathamesh.jbsolar.config.NeonStorageProperties;
import com.prathamesh.jbsolar.domain.StoredFile;
import com.prathamesh.jbsolar.domain.UploadPurpose;
import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.StoredFileRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.exception.SdkClientException;

@Service
@Transactional
public class MobileFileService {
    private static final Logger logger = LoggerFactory.getLogger(MobileFileService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf", "image/svg+xml");
    private static final Duration URL_LIFETIME = Duration.ofMinutes(10);
    private static final Duration PENDING_UPLOAD_LIFETIME = Duration.ofHours(24);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private final NeonStorageProperties storage;
    private final S3Presigner presigner;
    private final S3Client s3;
    private final StoredFileRepository files;
    private final VendorAgentRepository agents;
    private final FarmerRepository farmers;
    private final PolicyRepository policies;

    public MobileFileService(NeonStorageProperties storage, S3Presigner presigner,
            S3Client s3, StoredFileRepository files, VendorAgentRepository agents, FarmerRepository farmers,
            PolicyRepository policies) {
        this.storage = storage;
        this.presigner = presigner;
        this.s3 = s3;
        this.files = files;
        this.agents = agents;
        this.farmers = farmers;
        this.policies = policies;
    }

    public UploadUrlResponse createUploadUrl(UploadUrlRequest request, UserPrincipal principal) {
        var agent = requireAgent(principal);
        if (!storage.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unconfigured",
                    "Neon Object Storage is not configured");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType().toLowerCase(java.util.Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_file_type",
                    "Only JPEG, PNG, WebP images and PDF documents are accepted");
        }
        if (request.fileSize() < 1 || request.fileSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_file_size",
                    "File size must be between 1 byte and 10 MiB");
        }
        UploadPurpose purpose = resolvePurpose(request.resourceType(), request.purpose(), request.contentType());
        requireResourceAccess(request.resourceType(), request.resourceId(), agent.getVendor().getId());
        String key = "uploads/" + agent.getVendor().getId() + "/"
                + request.resourceType().name().toLowerCase(java.util.Locale.ROOT) + "/"
                + request.resourceId() + "/" + UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setResourceType(request.resourceType());
        file.setResourceId(request.resourceId());
        file.setPurpose(purpose);
        file.setStorageKey(key);
        file.setOriginalFilename(request.originalFilename().trim());
        file.setContentType(request.contentType().toLowerCase(java.util.Locale.ROOT));
        file.setFileSize(request.fileSize());
        file.setCreatedBy(agent);
        files.save(file);

        PutObjectRequest putRequest = PutObjectRequest.builder().bucket(storage.getBucket()).key(key)
                .contentType(file.getContentType()).contentLength(file.getFileSize()).build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(URL_LIFETIME).putObjectRequest(putRequest).build());
        if (!"https".equalsIgnoreCase(signed.url().getProtocol())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "secure_upload_url_unavailable",
                    "Neon Object Storage did not produce a secure upload URL");
        }
        return new UploadUrlResponse(file.getId(), signed.url().toString(), signed.expiration());
    }

    public FileMetadataResponse completeUpload(UUID fileId, UserPrincipal principal) {
        var agent = requireAgent(principal);
        if (!storage.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unconfigured",
                    "Neon Object Storage is not configured");
        }
        StoredFile file = files.findByIdAndCreatedById(fileId, agent.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "upload_not_found",
                        "Upload request was not found"));
        requireResourceAccess(file.getResourceType(), file.getResourceId(), agent.getVendor().getId());
        if (file.getUploadedAt() != null) {
            return toResponse(file);
        }
        try {
            var result = s3.headObject(HeadObjectRequest.builder().bucket(storage.getBucket())
                    .key(file.getStorageKey()).build());
            if (result.contentLength() == null || result.contentLength() != file.getFileSize()
                    || !file.getContentType().equalsIgnoreCase(result.contentType())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "uploaded_file_mismatch",
                        "Uploaded content does not match the declared file size and type");
            }
            file.setUploadedAt(Instant.now());
            return toResponse(file);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ApiException(HttpStatus.CONFLICT, "upload_not_found_in_storage",
                        "The file has not been uploaded to object storage");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                    "The uploaded file could not be verified in Neon Object Storage");
        } catch (SdkClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                    "The uploaded file could not be verified in Neon Object Storage");
        }
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listForResource(UploadResourceType resourceType,
            UUID resourceId, UserPrincipal principal) {
        var agent = requireAgent(principal);
        requireResourceAccess(resourceType, resourceId, agent.getVendor().getId());
        return files.findAllByResourceTypeAndResourceIdAndCreatedByIdAndUploadedAtIsNotNullOrderByCreatedAtDesc(
                        resourceType, resourceId, agent.getId()).stream().map(this::toResponse).toList();
    }

    private com.prathamesh.jbsolar.domain.VendorAgent requireAgent(UserPrincipal principal) {
        if (principal.role() != UserRole.VENDOR_AGENT || principal.agentId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "agent_access_required",
                    "This operation is available to vendor agents only");
        }
        return agents.findById(principal.agentId()).filter(agent -> agent.getDeletedAt() == null
                && agent.getStatus() == com.prathamesh.jbsolar.domain.RecordStatus.ACTIVE
                && agent.getVendor().getDeletedAt() == null
                && agent.getVendor().getStatus() == com.prathamesh.jbsolar.domain.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "agent_profile_unavailable",
                        "Agent profile is unavailable"));
    }

    private void requireResourceAccess(UploadResourceType type, UUID id, UUID vendorId) {
        boolean found = switch (type) {
            case FARMER -> farmers.findByIdAndCreatedByVendorId(id, vendorId)
                    .filter(row -> row.getDeletedAt() == null).isPresent();
            case POLICY -> policies.findById(id)
                    .filter(row -> row.getDeletedAt() == null
                            && row.getVendor().getId().equals(vendorId)).isPresent();
        };
        if (!found) {
            throw new ApiException(HttpStatus.NOT_FOUND, "upload_resource_not_found",
                    "The upload resource was not found in your vendor data");
        }
    }

    private FileMetadataResponse toResponse(StoredFile file) {
        return new FileMetadataResponse(file.getId(), file.getResourceType(), file.getResourceId(),
                file.getPurpose(), file.getOriginalFilename(), file.getContentType(), file.getFileSize(),
                file.getUploadedAt());
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup.fixed-delay-ms:3600000}")
    public void removeAbandonedUploads() {
        if (!storage.isConfigured()) {
            return;
        }
        List<StoredFile> abandoned = files.findAllByUploadedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                Instant.now().minus(PENDING_UPLOAD_LIFETIME), PageRequest.of(0, CLEANUP_BATCH_SIZE));
        for (StoredFile file : abandoned) {
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(storage.getBucket())
                        .key(file.getStorageKey()).build());
                files.delete(file);
            } catch (S3Exception | SdkClientException exception) {
                logger.warn("Could not clean abandoned upload {} from object storage", file.getId(), exception);
            }
        }
    }

    private UploadPurpose resolvePurpose(UploadResourceType resourceType, UploadPurpose purpose, String contentType) {
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (resourceType == UploadResourceType.FARMER) {
            if (purpose != null && purpose != UploadPurpose.FARMER_DOCUMENT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_upload_purpose",
                        "Farmer uploads must use the FARMER_DOCUMENT purpose");
            }
            return UploadPurpose.FARMER_DOCUMENT;
        }
        if (purpose != UploadPurpose.CUSTOMER_SIGNATURE && purpose != UploadPurpose.PUMP_SET_IMAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_upload_purpose",
                    "Policy uploads must use CUSTOMER_SIGNATURE or PUMP_SET_IMAGE");
        }
        if (!normalizedContentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "image_required",
                    "Policy signatures and pump-set photos must be images");
        }
        return purpose;
    }
}
