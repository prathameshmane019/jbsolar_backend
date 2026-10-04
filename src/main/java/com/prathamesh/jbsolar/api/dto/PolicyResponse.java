package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.prathamesh.jbsolar.domain.PolicyStatus;

public record PolicyResponse(UUID id, String policyNumber, UUID farmerId, String farmerName,
        UUID policyPlanId, String planName, UUID vendorId, LocalDate startDate, LocalDate endDate,
        BigDecimal amount, BigDecimal gstAmount, BigDecimal totalAmount, PolicyStatus status) {
}
