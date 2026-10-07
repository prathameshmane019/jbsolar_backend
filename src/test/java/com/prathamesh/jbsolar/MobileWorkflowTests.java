package com.prathamesh.jbsolar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.FarmerRequest;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.LoginRequest;
import com.prathamesh.jbsolar.api.dto.PolicyPlanRequest;
import com.prathamesh.jbsolar.api.dto.PolicyRequest;
import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.api.dto.UploadUrlRequest;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.domain.UploadPurpose;
import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.AgentService;
import com.prathamesh.jbsolar.service.AuthService;
import com.prathamesh.jbsolar.service.FarmerService;
import com.prathamesh.jbsolar.service.PaymentService;
import com.prathamesh.jbsolar.service.PolicyPlanService;
import com.prathamesh.jbsolar.service.PolicyService;
import com.prathamesh.jbsolar.service.VendorService;
import java.time.LocalDate;
import com.prathamesh.jbsolar.service.MobileFileService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@SpringBootTest
@ActiveProfiles("test")
class MobileWorkflowTests {
    @Autowired
    private VendorService vendors;
    @Autowired
    private AgentService agents;
    @Autowired
    private FarmerService farmers;
    @Autowired
    private AuthService authService;
    @Autowired
    private PolicyPlanService policyPlans;
    @Autowired
    private PolicyService policyService;
    @Autowired
    private PaymentService payments;
    @Autowired
    private MobileFileService mobileFiles;
    @MockitoBean
    private S3Client s3;

    @Test
    void agentLoginIsDistinctFromAdministratorWebLogin() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var login = new LoginRequest(agent.mobile(), "long-enough-test-password");

        assertEquals(UserRole.VENDOR_AGENT, authService.login(login, UserRole.VENDOR_AGENT).role());
        assertEquals(UserRole.VENDOR_AGENT, authService.login(
                new LoginRequest(agent.mobile().substring(3), login.password()), UserRole.VENDOR_AGENT).role());
        ApiException rejected = assertThrows(ApiException.class,
                () -> authService.login(login, UserRole.ADMIN));
        assertEquals("invalid_credentials", rejected.getCode());
        ApiException duplicateMobile = assertThrows(ApiException.class, () -> agents.create(
                new com.prathamesh.jbsolar.api.dto.AgentRequest(vendor.id(), "Duplicate Mobile",
                        agent.mobile().substring(3), "long-enough-test-password")));
        assertEquals("mobile_already_registered", duplicateMobile.getCode());
    }

    @Test
    void aadhaarIsUniqueAndFarmerAccessIsVendorScoped() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var principal = new UserPrincipal(agent.userId(), agent.id(), vendor.id(), agent.mobile(),
                UserRole.VENDOR_AGENT);
        var farmer = farmers.create(farmerRequest("234567891234"), principal);

        var aadhaarSearch = farmers.search(new DataQuery(0, 25, null, null,
                "234567891234", null, null, null, null), principal);
        assertEquals(1, aadhaarSearch.totalElements());
        assertEquals(farmer.id(), aadhaarSearch.content().get(0).id());

        ApiException duplicate = assertThrows(ApiException.class,
                () -> farmers.create(farmerRequest("234567891234"), principal));
        assertEquals("aadhaar_already_registered", duplicate.getCode());

        var otherVendor = createVendor();
        var otherAgent = createAgent(otherVendor.id());
        var otherPrincipal = new UserPrincipal(otherAgent.userId(), otherAgent.id(), otherVendor.id(),
                otherAgent.mobile(), UserRole.VENDOR_AGENT);
        ApiException hidden = assertThrows(ApiException.class,
                () -> farmers.get(farmer.id(), otherPrincipal));
        assertEquals("farmer_not_found", hidden.getCode());
    }

    @Test
    void farmerStoresPumpSetPowerAndMotorHead() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var principal = new UserPrincipal(agent.userId(), agent.id(), vendor.id(), agent.mobile(),
                UserRole.VENDOR_AGENT);
        var farmer = farmers.create(farmerRequest("567891234567"), principal);
        var plan = policyPlans.create(new PolicyPlanRequest("Pump Test Plan " + UUID.randomUUID(),
                null, 12, new BigDecimal("100.00"), new BigDecimal("18.00"), null));
        var request = new PolicyRequest(farmer.id(), plan.id(), LocalDate.now(), null,
                new BigDecimal("5.50"), new BigDecimal("100.00"));
        var policy = policyService.create(request, principal);
        assertEquals(new BigDecimal("5.50"), policy.pumpPowerHp());
        assertEquals(new BigDecimal("100.00"), policy.motorHeadMeters());

        var updated = policyService.update(policy.id(),
                new PolicyRequest(farmer.id(), plan.id(), LocalDate.now(), null,
                        new BigDecimal("7.50"), new BigDecimal("120.00")), principal);
        assertEquals(new BigDecimal("7.50"), updated.pumpPowerHp());
        assertEquals(new BigDecimal("120.00"), updated.motorHeadMeters());
    }

    @Test
    void policyImageUploadIsSignedAndCompletionIsVerifiedAndIdempotent() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var principal = new UserPrincipal(agent.userId(), agent.id(), vendor.id(), agent.mobile(),
                UserRole.VENDOR_AGENT);
        var farmer = farmers.create(farmerRequest("678912345678"), principal);
        var plan = policyPlans.create(new PolicyPlanRequest("Upload Test Plan " + UUID.randomUUID(),
                null, 12, new BigDecimal("100.00"), new BigDecimal("18.00"), null));
        var policy = policyService.create(
                new PolicyRequest(farmer.id(), plan.id(), LocalDate.now(), null, null, null), principal);
        var upload = mobileFiles.createUploadUrl(new UploadUrlRequest(UploadResourceType.POLICY,
                policy.id(), UploadPurpose.PUMP_SET_IMAGE, "pump.jpg", "image/jpeg", 2048), principal);

        assertTrue(upload.uploadUrl().startsWith("https://test-storage.c-2.us-east-2.aws.neon.tech/"));
        assertTrue(upload.uploadUrl().contains("X-Amz-Signature="));
        assertTrue(upload.uploadUrl().contains("content-length"), upload.uploadUrl());

        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(2047L).contentType("image/jpeg").build(),
                HeadObjectResponse.builder().contentLength(2048L).contentType("image/jpeg").build());
        ApiException mismatch = assertThrows(ApiException.class,
                () -> mobileFiles.completeUpload(upload.fileId(), principal));
        assertEquals("uploaded_file_mismatch", mismatch.getCode());

        var completed = mobileFiles.completeUpload(upload.fileId(), principal);
        assertEquals(UploadResourceType.POLICY, completed.resourceType());
        assertEquals(policy.id(), completed.resourceId());
        assertEquals(UploadPurpose.PUMP_SET_IMAGE, completed.purpose());
        assertEquals("pump.jpg", completed.originalFilename());
        assertEquals("image/jpeg", completed.contentType());
        assertEquals(2048L, completed.fileSize());
        assertTrue(completed.uploadedAt() != null);

        var repeatedCompletion = mobileFiles.completeUpload(upload.fileId(), principal);
        assertEquals(completed.id(), repeatedCompletion.id());
        verify(s3, times(2)).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void policyImageUploadRejectsAnotherVendorsPolicy() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var principal = new UserPrincipal(agent.userId(), agent.id(), vendor.id(), agent.mobile(),
                UserRole.VENDOR_AGENT);
        var otherVendor = createVendor();
        var otherAgent = createAgent(otherVendor.id());
        var otherPrincipal = new UserPrincipal(otherAgent.userId(), otherAgent.id(), otherVendor.id(),
                otherAgent.mobile(), UserRole.VENDOR_AGENT);
        var farmer = farmers.create(farmerRequest("789123456789"), otherPrincipal);
        var plan = policyPlans.create(new PolicyPlanRequest("Scoped Upload Plan " + UUID.randomUUID(),
                null, 12, new BigDecimal("100.00"), new BigDecimal("18.00"), null));
        var policy = policyService.create(
                new PolicyRequest(farmer.id(), plan.id(), LocalDate.now(), null, null, null), otherPrincipal);

        ApiException exception = assertThrows(ApiException.class, () -> mobileFiles.createUploadUrl(
                new UploadUrlRequest(UploadResourceType.POLICY, policy.id(), UploadPurpose.CUSTOMER_SIGNATURE,
                        "signature.jpg", "image/jpeg", 1024), principal));
        assertEquals("upload_resource_not_found", exception.getCode());
    }

    @Test
    void dummyPaymentActivatesPolicyAndCreatesOneInvoice() {
        var vendor = createVendor();
        var agent = createAgent(vendor.id());
        var principal = new UserPrincipal(agent.userId(), agent.id(), vendor.id(), agent.mobile(),
                UserRole.VENDOR_AGENT);
        var farmer = farmers.create(farmerRequest("345678912345"), principal);
        var plan = policyPlans.create(new PolicyPlanRequest("Test Plan " + UUID.randomUUID(),
                null, 12, new BigDecimal("100.00"), new BigDecimal("18.00"), null));
        var policy = policyService.create(new PolicyRequest(farmer.id(), plan.id(), LocalDate.now(), null, null, null),
                principal);

        var payment = payments.createDummyOrder(policy.id(), principal);
        assertEquals(new BigDecimal("118.00"), payment.amount());
        assertEquals("PENDING", payment.status().name());

        var paid = payments.simulateDummySuccess(payment.id(), principal);
        assertEquals("SUCCESS", paid.status().name());
        var invoice = payments.getInvoiceForPolicy(policy.id(), principal);
        assertEquals(payment.id(), invoice.paymentId());
        assertEquals(new BigDecimal("100.00"), invoice.amount());
        assertEquals(new BigDecimal("18.00"), invoice.gstAmount());
        assertEquals(new BigDecimal("118.00"), invoice.totalAmount());
        assertEquals(invoice.id(), payments.simulateDummySuccess(payment.id(), principal).invoiceId());
        assertEquals("ACTIVE", policyService.get(policy.id(), principal).status().name());
    }

    private com.prathamesh.jbsolar.api.dto.VendorResponse createVendor() {
        String nonce = UUID.randomUUID().toString();
        return vendors.create(new VendorRequest("Mobile Workflow " + nonce, "Test Contact",
                "9000000000", nonce + "@example.test"));
    }

    private com.prathamesh.jbsolar.api.dto.AgentResponse createAgent(UUID vendorId) {
        return agents.create(new com.prathamesh.jbsolar.api.dto.AgentRequest(vendorId, "Mobile Agent",
                "9" + String.format(java.util.Locale.ROOT, "%09d",
                        Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L)),
                "long-enough-test-password"));
    }

    private FarmerRequest farmerRequest(String aadhaar) {
        return new FarmerRequest("Test Farmer", "9876543210", aadhaar, "Test address",
                "Pune", "Haveli", "Village");
    }
}
