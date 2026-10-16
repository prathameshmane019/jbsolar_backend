package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(@NotNull UUID farmerId, @NotNull UUID policyPlanId,
        @NotNull LocalDate startDate, UUID vendorId,
        @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal pumpPowerHp,
        @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal motorHeadMeters) {
}
