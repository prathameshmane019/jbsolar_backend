package com.prathamesh.jbsolar.service;

import java.time.Duration;
import java.time.Instant;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import com.prathamesh.jbsolar.config.ObjectStorageProperties;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.exception.SdkClientException;

@Service
@Transactional
public class MobileFileService {
    private static final Logger logger = LoggerFactory.getLogger(MobileFileService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf", "image/svg+xml");
    private static final Duration URL_LIFETIME = Duration.ofMinutes(10);
    private static final Duration VIEW_URL_LIFETIME = Duration.ofHours(1);
    private static final Duration PENDING_UPLOAD_LIFETIME = Duration.ofHours(24);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private final ObjectStorageProperties storage;
    private final S3Presigner presigner;
    private final S3Client s3;
    private final StoredFileRepository files;
    private final VendorAgentRepository agents;
    private final FarmerRepository farmers;
    private final PolicyRepository policies;

    public MobileFileService(ObjectStorageProperties storage, S3Presigner presigner,
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
                    "Oracle Object Storage is not configured");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType().toLowerCase(java.util.Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_file_type",
                    "Only JPEG, PNG, WebP images and PDF documents are accepted");
        }
        if (request.fileSize() < 1 || request.fileSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_file_size",
                    "File size must be between 1 byte and 10 MiB");
        }
        if (files.getTotalReservedBytes() > storage.getMaxBytes() - request.fileSize()) {
            throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "storage_limit_reached",
                    "The configured object-storage limit has been reached");
        }
        UploadPurpose purpose = resolvePurpose(request.resourceType(), request.purpose(), request.contentType());
        requireResourceAccess(request.resourceType(), request.resourceId(), agent.getVendor().getId());
        String folder = request.resourceType() == UploadResourceType.POLICY ? "policies" : "farmers";
        String key = folder + "/" + request.resourceId() + "/" + UUID.randomUUID();
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

        PutObjectRequest putRequest = PutObjectRequest.builder().bucket(storage.getBucket()).key(key).build();
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(URL_LIFETIME).putObjectRequest(putRequest).build());
        if (!"https".equalsIgnoreCase(signed.url().getProtocol())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "secure_upload_url_unavailable",
                    "Oracle Object Storage did not produce a secure upload URL");
        }
        logger.info("Prepared object upload: fileId={} endpointHost={} region={} namespace={} bucket={} "
                        + "objectKey={} contentType={} declaredBytes={} signingScope={} signedHeaders={} "
                        + "expiresAt={} accessKeyIdSuffix={}",
                file.getId(), signed.url().getHost(), storage.getRegion(), storage.getNamespace(),
                storage.getBucket(), key, file.getContentType(), file.getFileSize(),
                getSigningScope(signed.url().getQuery()), getSignedHeaders(signed.url().getQuery()),
                signed.expiration(), maskedAccessKeySuffix());
        return new UploadUrlResponse(file.getId(), signed.url().toString(), signed.expiration());
    }

    public FileMetadataResponse completeUpload(UUID fileId, UserPrincipal principal) {
        var agent = requireAgent(principal);
        if (!storage.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unconfigured",
                    "Oracle Object Storage is not configured");
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
            logger.info("Verified object upload: fileId={} endpointHost={} region={} bucket={} objectKey={} "
                            + "expectedContentType={} actualContentType={} expectedBytes={} actualBytes={}",
                    file.getId(), storage.getEndpointHost(), storage.getRegion(), storage.getBucket(),
                    file.getStorageKey(), file.getContentType(), result.contentType(), file.getFileSize(),
                    result.contentLength());
            if (result.contentLength() == null || result.contentLength() != file.getFileSize()
                    || !file.getContentType().equalsIgnoreCase(result.contentType())) {
                deleteInvalidUpload(file);
                throw new ApiException(HttpStatus.BAD_REQUEST, "uploaded_file_mismatch",
                        "Uploaded content does not match the declared file size and type");
            }
            file.setUploadedAt(Instant.now());
            return toResponse(file);
        } catch (S3Exception exception) {
            logger.error("OCI object verification failed: fileId={} endpointHost={} region={} bucket={} "
                            + "objectKey={} httpStatus={} serviceErrorCode={} requestId={}",
                    file.getId(), storage.getEndpointHost(), storage.getRegion(), storage.getBucket(),
                    file.getStorageKey(), exception.statusCode(),
                    exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode(),
                    exception.requestId(), exception);
            if (exception.statusCode() == 404) {
                throw new ApiException(HttpStatus.CONFLICT, "upload_not_found_in_storage",
                        "The file has not been uploaded to object storage");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                    "The uploaded file could not be verified in Oracle Object Storage");
        } catch (SdkClientException exception) {
            logger.error("OCI object verification request failed: fileId={} endpointHost={} region={} bucket={} "
                            + "objectKey={}",
                    file.getId(), storage.getEndpointHost(), storage.getRegion(), storage.getBucket(),
                    file.getStorageKey(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                    "The uploaded file could not be verified in Oracle Object Storage");
        }
    }

    private void deleteInvalidUpload(StoredFile file) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(storage.getBucket())
                    .key(file.getStorageKey()).build());
        } catch (S3Exception | SdkClientException exception) {
            logger.error("Could not remove invalid upload {} from Oracle Object Storage", file.getId(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                    "The invalid upload could not be removed from Oracle Object Storage");
        }
    }

    private String getSignedHeaders(String query) {
        return getQueryParameter(query, "X-Amz-SignedHeaders");
    }

    private String getSigningScope(String query) {
        String credential = getQueryParameter(query, "X-Amz-Credential");
        int accessKeySeparator = credential.indexOf('/');
        return accessKeySeparator < 0 ? "unknown" : credential.substring(accessKeySeparator + 1);
    }

    private String getQueryParameter(String query, String parameterName) {
        if (query == null || query.isBlank()) {
            return "unknown";
        }
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String name = URLDecoder.decode(parameter.substring(0, separator), StandardCharsets.UTF_8);
            if (name.equals(parameterName)) {
                return URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return "unknown";
    }

    private String maskedAccessKeySuffix() {
        String accessKeyId = storage.getAccessKeyId();
        if (accessKeyId == null || accessKeyId.isBlank()) {
            return "not-configured";
        }
        return "****" + accessKeyId.substring(Math.max(0, accessKeyId.length() - 4));
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listForResource(UploadResourceType resourceType,
            UUID resourceId, UserPrincipal principal) {
        var agent = requireAgent(principal);
        requireResourceAccess(resourceType, resourceId, agent.getVendor().getId());
        return files.findAllByResourceTypeAndResourceIdAndCreatedByIdAndUploadedAtIsNotNullOrderByCreatedAtDesc(
                        resourceType, resourceId, agent.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listForPolicy(UUID policyId) {
        if (policies.findById(policyId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "policy_not_found", "Policy was not found");
        }
        return files.findAllByResourceTypeAndResourceIdAndUploadedAtIsNotNullOrderByCreatedAtDesc(
                UploadResourceType.POLICY, policyId).stream().map(this::toResponse).toList();
    }

    public void deletePolicyFiles(UUID policyId) {
        List<StoredFile> policyFiles = files.findAllByResourceTypeAndResourceId(
                UploadResourceType.POLICY, policyId);
        if (policyFiles.isEmpty()) {
            return;
        }
        if (!storage.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unconfigured",
                    "Oracle Object Storage is required to remove this policy's files");
        }
        for (StoredFile file : policyFiles) {
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(storage.getBucket())
                        .key(file.getStorageKey()).build());
            } catch (S3Exception | SdkClientException exception) {
                logger.error("Could not remove policy file {} from Oracle Object Storage", file.getId(), exception);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "object_storage_unavailable",
                        "Policy files could not be removed from Oracle Object Storage");
            }
        }
        files.deleteAll(policyFiles);
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
        String viewUrl = null;
        Instant viewUrlExpiresAt = null;
        if (file.getUploadedAt() != null) {
            if (!storage.isConfigured()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unconfigured",
                        "Oracle Object Storage is not configured");
            }
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(storage.getBucket())
                    .key(file.getStorageKey())
                    .responseContentType(file.getContentType())
                    .responseContentDisposition("inline")
                    .build();
            var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(VIEW_URL_LIFETIME)
                    .getObjectRequest(getRequest)
                    .build());
            if (!"https".equalsIgnoreCase(signed.url().getProtocol())) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "secure_view_url_unavailable",
                        "Oracle Object Storage did not produce a secure viewing URL");
            }
            viewUrl = signed.url().toString();
            viewUrlExpiresAt = signed.expiration();
        }
        return new FileMetadataResponse(file.getId(), file.getResourceType(), file.getResourceId(),
                file.getPurpose(), file.getOriginalFilename(), file.getContentType(), file.getFileSize(),
                file.getUploadedAt(), viewUrl, viewUrlExpiresAt);
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
