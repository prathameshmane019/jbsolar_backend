package com.prathamesh.jbsolar.api.dto;

import java.util.UUID;
import com.prathamesh.jbsolar.domain.RecordStatus;

public record AgentResponse(UUID id, UUID userId, UUID vendorId, String vendorName, String fullName,
        String mobile, RecordStatus status) {
}
