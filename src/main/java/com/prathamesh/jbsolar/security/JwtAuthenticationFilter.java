package com.prathamesh.jbsolar.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.prathamesh.jbsolar.domain.AccountStatus;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.domain.User;
import com.prathamesh.jbsolar.domain.VendorAgent;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.UserRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final VendorAgentRepository agentRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
            VendorAgentRepository agentRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        logger.info("Incoming request: {} {} from {}", request.getMethod(), request.getRequestURL(),
                request.getRemoteAddr());
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                User user = userRepository.findById(jwtService.getUserId(header.substring(7))).orElse(null);
                if (user != null && user.getStatus() == AccountStatus.ACTIVE) {
                    VendorAgent agent = user.getRole() == UserRole.VENDOR_AGENT
                            ? agentRepository.findWithVendorByUserId(user.getId()).orElse(null) : null;
                    if (user.getRole() != UserRole.VENDOR_AGENT || (agent != null
                        && agent.getStatus() == RecordStatus.ACTIVE
                        && agent.getDeletedAt() == null
                        && agent.getVendor().getDeletedAt() == null
                        && agent.getVendor().getStatus() == RecordStatus.ACTIVE)) {
                        UserPrincipal principal = new UserPrincipal(user.getId(),
                                agent == null ? null : agent.getId(),
                                agent == null ? null : agent.getVendor().getId(), user.getMobile(), user.getRole());
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException exception) {
                logger.debug("Rejecting invalid bearer token for {} {}", request.getMethod(),
                        request.getRequestURI());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
