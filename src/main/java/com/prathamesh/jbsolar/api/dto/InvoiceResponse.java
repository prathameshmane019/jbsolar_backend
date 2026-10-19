package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(UUID id, String invoiceNumber, UUID policyId, String policyNumber,
        UUID paymentId, LocalDate invoiceDate, BigDecimal amount, BigDecimal gstAmount,
        BigDecimal totalAmount, Instant createdAt, LocalDate policyStartDate, LocalDate policyEndDate,
        BigDecimal pumpPowerHp, BigDecimal motorHeadMeters, String planName, String planCode,
        Integer durationMonths, BigDecimal gstPercentage, String farmerName, String farmerCode,
        String farmerMobile, String farmerAddress, String vendorName, String vendorCode,
        String vendorMobile, String vendorEmail, String paymentNumber, String paymentMethod,
        String gateway, String gatewayPaymentId, Instant paidAt) {
}

