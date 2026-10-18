package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Everything the admin portal needs to show and print an invoice. */
public record InvoiceDetailResponse(
        UUID id, String invoiceNumber, LocalDate invoiceDate, Instant createdAt,
        BigDecimal amount, BigDecimal gstAmount, BigDecimal totalAmount, BigDecimal gstPercentage,
        UUID policyId, String policyNumber, LocalDate policyStartDate, LocalDate policyEndDate,
        BigDecimal pumpPowerHp, BigDecimal motorHeadMeters,
        String planName, String planCode, Integer durationMonths,
        String farmerName, String farmerCode, String farmerMobile, String farmerAddress,
        String vendorName, String vendorCode, String vendorMobile, String vendorEmail,
        UUID paymentId, String paymentNumber, String paymentMethod, String gateway,
        String gatewayPaymentId, Instant paidAt) {
}