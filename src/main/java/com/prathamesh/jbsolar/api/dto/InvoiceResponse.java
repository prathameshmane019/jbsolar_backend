package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(UUID id, String invoiceNumber, UUID policyId, String policyNumber,
        UUID paymentId, LocalDate invoiceDate, BigDecimal amount, BigDecimal gstAmount,
        BigDecimal totalAmount, Instant createdAt) {
}
