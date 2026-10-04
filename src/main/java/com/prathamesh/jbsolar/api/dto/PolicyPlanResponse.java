package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.prathamesh.jbsolar.domain.RecordStatus;

public record PolicyPlanResponse(UUID id, String name, String description, int durationMonths,
        BigDecimal price, BigDecimal gstPercentage, String termsAndConditions, RecordStatus status) {
}
