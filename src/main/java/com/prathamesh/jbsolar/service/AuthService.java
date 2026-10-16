package com.prathamesh.jbsolar.service;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.LoginRequest;
import com.prathamesh.jbsolar.api.dto.TokenResponse;
import com.prathamesh.jbsolar.domain.AccountStatus;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.UserRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.security.JwtService;
import com.prathamesh.jbsolar.service.MobileNumber;

@Service
public class AuthService {
    private final UserRepository users;
    private final VendorAgentRepository agents;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, VendorAgentRepository agents, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.agents = agents;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, UserRole requiredRole) {
        var user = users.findByMobile(MobileNumber.normalize(request.mobile())).orElseThrow(this::invalidCredentials);
        if (user.getRole() != requiredRole || user.getStatus() != AccountStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (user.getRole() == UserRole.VENDOR_AGENT && agents.findByUserId(user.getId())
                .filter(agent -> agent.getStatus() == RecordStatus.ACTIVE
                        && agent.getDeletedAt() == null
                        && agent.getVendor().getDeletedAt() == null
                        && agent.getVendor().getStatus() == RecordStatus.ACTIVE).isEmpty()) {
            throw invalidCredentials();
        }
        user.setLastLoginAt(Instant.now());
        return new TokenResponse(jwtService.createToken(user), "Bearer", jwtService.getExpirationSeconds(),
                user.getId(), user.getMobile(), user.getRole());
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Mobile number or password is incorrect");
    }
}
