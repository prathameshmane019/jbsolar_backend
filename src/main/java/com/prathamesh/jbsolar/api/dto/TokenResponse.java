package com.prathamesh.jbsolar.api.dto;

import java.util.UUID;
import com.prathamesh.jbsolar.domain.UserRole;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UUID userId, String mobile,
        UserRole role) {
}
