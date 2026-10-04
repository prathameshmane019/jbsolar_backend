package com.prathamesh.jbsolar.api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PolicyPlanRequest(@NotBlank @Size(max = 150) String name, @Size(max = 2000) String description,
        @NotNull @DecimalMin("1") Integer durationMonths,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotNull @DecimalMin("0") BigDecimal gstPercentage, @Size(max = 5000) String termsAndConditions) {
}
