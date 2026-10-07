package com.prathamesh.jbsolar.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadUrlResponse(UUID fileId, String uploadUrl, Instant expiresAt) {
}
