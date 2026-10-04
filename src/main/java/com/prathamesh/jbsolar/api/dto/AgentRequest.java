package com.prathamesh.jbsolar.api.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentRequest(@NotNull UUID vendorId, @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Pattern(regexp = "^[+0-9][0-9+ -]{7,19}$") String mobile,
        @NotBlank @Size(min = 10, max = 100) String initialPassword) {
}
