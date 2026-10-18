package com.prathamesh.jbsolar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.UploadUrlRequest;
import com.prathamesh.jbsolar.config.ObjectStorageProperties;
import com.prathamesh.jbsolar.domain.Farmer;
import com.prathamesh.jbsolar.domain.Policy;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.domain.StoredFile;
import com.prathamesh.jbsolar.domain.UploadPurpose;
import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.domain.VendorAgent;
import com.prathamesh.jbsolar.domain.Vendor;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.StoredFileRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.MobileFileService;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ObjectStorageTests {
    @Test
    void oracleEndpointAndAlwaysFreeLimitAreConfiguredFromNamespaceAndRegion() {
        ObjectStorageProperties properties = configuredStorage();
        properties.setEndpoint("");
        properties.setNamespace("testnamespace");

        assertTrue(properties.isConfigured());
        assertEquals("https://testnamespace.compat.objectstorage.us-ashburn-1.oci.customer-oci.com",
                properties.getEndpoint());
        assertEquals(20_000_000_000L, properties.getMaxBytes());
    }

    @Test
    void policyFilesHaveTemporaryInlineViewUrlsForTheAdminFrontend() {
        UUID policyId = UUID.randomUUID();
        ObjectStorageProperties storage = configuredStorage();
        StoredFile file = uploadedPolicyFile(policyId);
        StoredFileRepository files = mock(StoredFileRepository.class);
        PolicyRepository policies = mock(PolicyRepository.class);
        when(policies.findById(policyId)).thenReturn(Optional.of(new Policy()));
        when(files.findAllByResourceTypeAndResourceIdAndUploadedAtIsNotNullOrderByCreatedAtDesc(
                UploadResourceType.POLICY, policyId)).thenReturn(List.of(file));

        try (S3Presigner presigner = presigner(storage)) {
            MobileFileService service = service(storage, presigner, mock(S3Client.class), files, policies);
            var response = service.listForPolicy(policyId).getFirst();

            assertTrue(response.viewUrl().startsWith(storage.getEndpoint()));
            assertTrue(response.viewUrl().contains("X-Amz-Signature="));
            assertTrue(response.viewUrl().contains("response-content-disposition"));
            assertTrue(response.viewUrlExpiresAt().isAfter(Instant.now()));
        }
    }

    @Test
    void uploadSignatureDoesNotRequireNativeClientToSetContentLength() {
        UUID agentId = UUID.randomUUID();
        UUID farmerId = UUID.randomUUID();
        ObjectStorageProperties storage = configuredStorage();
        StoredFileRepository files = mock(StoredFileRepository.class);
        VendorAgentRepository agents = mock(VendorAgentRepository.class);
        FarmerRepository farmers = mock(FarmerRepository.class);
        Vendor vendor = mock(Vendor.class);
        VendorAgent agent = mock(VendorAgent.class);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.getId()).thenReturn(agentId);
        when(agent.getDeletedAt()).thenReturn(null);
        when(agent.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(agent.getVendor()).thenReturn(vendor);
        when(vendor.getDeletedAt()).thenReturn(null);
        when(vendor.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(vendor.getId()).thenReturn(UUID.randomUUID());
        Farmer farmer = mock(Farmer.class);
        when(farmers.findByIdAndCreatedByVendorId(farmerId, vendor.getId()))
                .thenReturn(Optional.of(farmer));
        when(farmer.getDeletedAt()).thenReturn(null);
        when(files.getTotalReservedBytes()).thenReturn(0L);

        try (S3Presigner presigner = presigner(storage)) {
            MobileFileService service = new MobileFileService(storage, presigner, mock(S3Client.class),
                    files, agents, farmers, mock(PolicyRepository.class));
            var upload = service.createUploadUrl(new UploadUrlRequest(UploadResourceType.FARMER,
                    farmerId, UploadPurpose.FARMER_DOCUMENT, "photo.jpg", "image/jpeg", 2048),
                    new UserPrincipal(UUID.randomUUID(), agentId, vendor.getId(),
                            "+919000000000", UserRole.VENDOR_AGENT));
            String signedHeaders = java.net.URLDecoder.decode(
                    java.net.URI.create(upload.uploadUrl()).getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(signedHeaders.contains("X-Amz-SignedHeaders=host"));
            org.junit.jupiter.api.Assertions.assertFalse(signedHeaders.contains("content-type"));
            org.junit.jupiter.api.Assertions.assertFalse(signedHeaders.contains("content-length"));
            assertTrue(signedHeaders.contains("X-Amz-Credential="));
            assertTrue(signedHeaders.contains("us-ashburn-1/s3/aws4_request"));
        }
    }

    @Test
    void policyPurgeDeletesItsObjectsAndMetadata() {
        UUID policyId = UUID.randomUUID();
        ObjectStorageProperties storage = configuredStorage();
        StoredFile file = uploadedPolicyFile(policyId);
        StoredFileRepository files = mock(StoredFileRepository.class);
        S3Client s3 = mock(S3Client.class);
        when(files.findAllByResourceTypeAndResourceId(UploadResourceType.POLICY, policyId))
                .thenReturn(List.of(file));
        try (S3Presigner presigner = presigner(storage)) {
            MobileFileService service = service(storage, presigner, s3, files, mock(PolicyRepository.class));

            service.deletePolicyFiles(policyId);

            verify(s3).deleteObject(any(DeleteObjectRequest.class));
            verify(files).deleteAll(List.of(file));
        }
    }

    @Test
    void uploadIsRejectedWhenTheConfiguredStorageAllowanceIsAlreadyReserved() {
        UUID agentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ObjectStorageProperties storage = configuredStorage();
        storage.setMaxBytes(10);
        StoredFileRepository files = mock(StoredFileRepository.class);
        VendorAgentRepository agents = mock(VendorAgentRepository.class);
        VendorAgent agent = mock(VendorAgent.class);
        var vendor = mock(com.prathamesh.jbsolar.domain.Vendor.class);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.getDeletedAt()).thenReturn(null);
        when(agent.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(agent.getVendor()).thenReturn(vendor);
        when(vendor.getDeletedAt()).thenReturn(null);
        when(vendor.getStatus()).thenReturn(RecordStatus.ACTIVE);
        when(files.getTotalReservedBytes()).thenReturn(10L);

        try (S3Presigner presigner = presigner(storage)) {
            MobileFileService service = new MobileFileService(storage, presigner, mock(S3Client.class),
                    files, agents, mock(FarmerRepository.class), mock(PolicyRepository.class));
            UserPrincipal principal = new UserPrincipal(userId, agentId, UUID.randomUUID(),
                    "+919000000000", UserRole.VENDOR_AGENT);
            UploadUrlRequest request = new UploadUrlRequest(UploadResourceType.FARMER, UUID.randomUUID(),
                    UploadPurpose.FARMER_DOCUMENT, "document.pdf", "application/pdf", 1);

            ApiException exception = assertThrows(ApiException.class,
                    () -> service.createUploadUrl(request, principal));

            assertEquals("storage_limit_reached", exception.getCode());
        }
    }

    private ObjectStorageProperties configuredStorage() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setNamespace("testnamespace");
        properties.setBucket("private-test-bucket");
        properties.setAccessKeyId("test-access-key");
        properties.setSecretAccessKey("test-secret-key");
        properties.setRegion("us-ashburn-1");
        return properties;
    }

    private StoredFile uploadedPolicyFile(UUID policyId) {
        StoredFile file = new StoredFile();
        file.setResourceType(UploadResourceType.POLICY);
        file.setResourceId(policyId);
        file.setStorageKey("policies/" + policyId + "/" + UUID.randomUUID());
        file.setOriginalFilename("pump.jpg");
        file.setContentType("image/jpeg");
        file.setFileSize(2048);
        file.setUploadedAt(Instant.now());
        return file;
    }

    private S3Presigner presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey())))
                .endpointOverride(java.net.URI.create(properties.getEndpoint()))
                .build();
    }

    private MobileFileService service(ObjectStorageProperties storage, S3Presigner presigner, S3Client s3,
            StoredFileRepository files, PolicyRepository policies) {
        return new MobileFileService(storage, presigner, s3, files, mock(VendorAgentRepository.class),
                mock(FarmerRepository.class), policies);
    }
}
