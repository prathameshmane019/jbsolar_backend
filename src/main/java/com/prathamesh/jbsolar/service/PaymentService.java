package com.prathamesh.jbsolar.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.InvoiceResponse;
import com.prathamesh.jbsolar.api.dto.PaymentResponse;
import com.prathamesh.jbsolar.domain.Invoice;
import com.prathamesh.jbsolar.domain.Payment;
import com.prathamesh.jbsolar.domain.PaymentStatus;
import com.prathamesh.jbsolar.domain.PolicyStatus;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.InvoiceRepository;
import com.prathamesh.jbsolar.repository.PaymentRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

@Service
@Transactional
public class PaymentService {
    private final PolicyRepository policies;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final boolean dummyPaymentsEnabled;

    public PaymentService(PolicyRepository policies, PaymentRepository payments, InvoiceRepository invoices,
            @Value("${app.payment.dummy-enabled:true}") boolean dummyPaymentsEnabled) {
        this.policies = policies;
        this.payments = payments;
        this.invoices = invoices;
        this.dummyPaymentsEnabled = dummyPaymentsEnabled;
    }

    public PaymentResponse createDummyOrder(UUID policyId, UserPrincipal principal) {
        requireDummyPaymentsEnabled();
        var policy = policies.findByIdForUpdate(policyId)
                .filter(row -> row.getDeletedAt() == null && canAccess(row.getVendor().getId(), principal))
                .orElseThrow(this::policyNotFound);
        if (policy.getStatus() != PolicyStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "policy_not_payable",
                    "Only pending policies can start a payment");
        }
        var existing = payments.findFirstByPolicyIdAndStatusOrderByCreatedAtDesc(policyId, PaymentStatus.PENDING);
        if (existing.isPresent()) {
            return toPaymentResponse(existing.get());
        }

        String reference = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setPolicy(policy);
        payment.setPaymentNumber("JB-PAY-" + reference.substring(0, 12).toUpperCase(java.util.Locale.ROOT));
        payment.setGateway("DUMMY");
        payment.setGatewayOrderId("dummy-order-" + reference);
        payment.setAmount(policy.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        return toPaymentResponse(payments.save(payment));
    }

    public PaymentResponse simulateDummySuccess(UUID paymentId, UserPrincipal principal) {
        requireDummyPaymentsEnabled();
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(this::paymentNotFound);
        var policy = policies.findByIdForUpdate(payment.getPolicy().getId())
                .filter(row -> row.getDeletedAt() == null && canAccess(row.getVendor().getId(), principal))
                .orElseThrow(this::policyNotFound);
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return toPaymentResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING || policy.getStatus() != PolicyStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "payment_not_pending",
                    "Only a pending payment for a pending policy can be confirmed");
        }

        Instant paidAt = Instant.now();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("dummy-payment-" + UUID.randomUUID());
        payment.setPaymentMethod("DUMMY");
        payment.setPaidAt(paidAt);
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setPurchasedAt(paidAt);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("JB-INV-" + UUID.randomUUID().toString()
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT));
        invoice.setPolicy(policy);
        invoice.setPayment(payment);
        invoice.setInvoiceDate(LocalDate.now(ZoneOffset.UTC));
        invoice.setAmount(policy.getAmount());
        invoice.setGstAmount(policy.getGstAmount());
        invoice.setTotalAmount(policy.getTotalAmount());
        invoices.save(invoice);
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID invoiceId, UserPrincipal principal) {
        var invoice = principal.role() == UserRole.ADMIN
                ? invoices.findById(invoiceId)
                : invoices.findByIdAndPolicyVendorId(invoiceId, principal.vendorId());
        return invoice.map(this::toInvoiceResponse).orElseThrow(this::invoiceNotFound);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceForPolicy(UUID policyId, UserPrincipal principal) {
        var policy = policies.findById(policyId)
                .filter(row -> row.getDeletedAt() == null && canAccess(row.getVendor().getId(), principal))
                .orElseThrow(this::policyNotFound);
        return invoices.findByPolicyId(policy.getId()).map(this::toInvoiceResponse)
                .orElseThrow(this::invoiceNotFound);
    }

    private boolean canAccess(UUID vendorId, UserPrincipal principal) {
        return principal.role() == UserRole.ADMIN || vendorId.equals(principal.vendorId());
    }

    private void requireDummyPaymentsEnabled() {
        if (!dummyPaymentsEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "dummy_payments_disabled",
                    "Dummy payment simulation is disabled");
        }
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        UUID invoiceId = invoices.findByPolicyId(payment.getPolicy().getId())
                .map(Invoice::getId).orElse(null);
        return new PaymentResponse(payment.getId(), payment.getPaymentNumber(), payment.getPolicy().getId(),
                payment.getGateway(), payment.getGatewayOrderId(), payment.getAmount(), payment.getStatus(),
                invoiceId);
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getPolicy().getId(),
                invoice.getPolicy().getPolicyNumber(), invoice.getPayment().getId(), invoice.getInvoiceDate(),
                invoice.getAmount(), invoice.getGstAmount(), invoice.getTotalAmount(), invoice.getCreatedAt());
    }

    private ApiException policyNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "policy_not_found", "Policy was not found");
    }

    private ApiException paymentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "payment_not_found", "Payment was not found");
    }

    private ApiException invoiceNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "invoice_not_found", "Invoice was not found");
    }
}
