package com.prathamesh.jbsolar.api.dto;

import java.time.Instant;
import java.util.UUID;

public record FarmerResponse(UUID id, String customerCode, String fullName, String mobile, String address,
        String district, String taluka, String village, UUID createdByAgentId, Instant createdAt) {
}
