package com.prathamesh.jbsolar.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorRequest(@NotBlank @Size(max = 150) String name,
        @Size(max = 100) String contactPerson, @Size(max = 20) String mobile, @Email @Size(max = 150) String email) {
}
