package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.InvoiceResponse;
import com.prathamesh.jbsolar.api.dto.PaymentResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDOR_AGENT')")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/policies/{policyId}/payments/dummy-order")
    public ResponseEntity<PaymentResponse> createDummyOrder(@PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentResponse payment = payments.createDummyOrder(policyId, principal);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }

    @PostMapping("/payments/{paymentId}/simulate-success")
    public PaymentResponse simulateSuccess(@PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payments.simulateDummySuccess(paymentId, principal);
    }

    @GetMapping("/policies/{policyId}/invoice")
    public InvoiceResponse getInvoiceForPolicy(@PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payments.getInvoiceForPolicy(policyId, principal);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse getInvoice(@PathVariable UUID invoiceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payments.getInvoice(invoiceId, principal);
    }
}
