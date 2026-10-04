package com.prathamesh.jbsolar.api.dto;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(@NotNull UUID farmerId, @NotNull UUID policyPlanId,
        @NotNull LocalDate startDate, UUID vendorId) {
}
