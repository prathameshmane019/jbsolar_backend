package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.prathamesh.jbsolar.domain.PaymentStatus;

public record PaymentResponse(UUID id, String paymentNumber, UUID policyId, String gateway,
        String gatewayOrderId, BigDecimal amount, PaymentStatus status, UUID invoiceId) {
}
