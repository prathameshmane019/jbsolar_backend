package com.prathamesh.jbsolar.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FarmerRequest(@NotBlank @Size(max = 150) String fullName,
        @NotBlank @Pattern(regexp = "^[+0-9][0-9+ -]{7,19}$") String mobile,
        @Size(max = 2000) String address, @Size(max = 100) String district,
        @Size(max = 100) String taluka, @Size(max = 100) String village) {
}
