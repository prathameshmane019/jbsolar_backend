package com.prathamesh.jbsolar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.prathamesh.jbsolar.security.ApiRateLimitFilter;

class ApiRateLimitFilterTests {
    @Test
    void limitsLoginAttemptsByClientAddressAndReturnsRetryAfter() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter();
        SecurityContextHolder.clearContext();

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse response = invoke(filter);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse limitedResponse = invoke(filter);
        assertEquals(429, limitedResponse.getStatus());
        assertTrue(limitedResponse.getContentAsString().contains("\"error\":\"rate_limit_exceeded\""));
        assertNotNull(limitedResponse.getHeader("Retry-After"));
    }

    private MockHttpServletResponse invoke(ApiRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
