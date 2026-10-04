package com.prathamesh.jbsolar.security;

import java.util.UUID;

import com.prathamesh.jbsolar.domain.UserRole;

public record UserPrincipal(UUID userId, UUID agentId, UUID vendorId, String mobile, UserRole role) {
}
