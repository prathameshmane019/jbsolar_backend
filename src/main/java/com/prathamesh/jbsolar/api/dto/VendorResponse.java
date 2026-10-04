package com.prathamesh.jbsolar.api.dto;

import java.time.Instant;
import java.util.UUID;
import com.prathamesh.jbsolar.domain.RecordStatus;

public record VendorResponse(UUID id, String name, String contactPerson, String mobile, String email,
        RecordStatus status, Instant createdAt) {
}
