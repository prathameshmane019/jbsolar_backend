package com.prathamesh.jbsolar.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int LOGIN_LIMIT = 10;
    private static final int EXPORT_LIMIT = 2;
    private static final int AUTHENTICATED_LIMIT = 120;
    private static final int ANONYMOUS_LIMIT = 60;
    private static final int MAX_TRACKED_CLIENTS = 20_000;
    private static final long CLEANUP_INTERVAL = 256;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long windowNumber = now / WINDOW_MILLIS;
        String remoteAddress = request.getRemoteAddr();
        UserPrincipal user = authenticatedUser();
        String key;
        int limit;

        if ("/api/v1/auth/login".equals(path) || "/api/v1/auth/agent/login".equals(path)) {
            key = "login:" + remoteAddress;
            limit = LOGIN_LIMIT;
        } else if ("/api/v1/exports".equals(path) || path.startsWith("/api/v1/exports/")) {
            key = clientKey("export:", remoteAddress, user);
            limit = EXPORT_LIMIT;
        } else {
            key = clientKey("api:", remoteAddress, user);
            limit = user == null ? ANONYMOUS_LIMIT : AUTHENTICATED_LIMIT;
        }

        if (!tryAcquire(key, limit, windowNumber)) {
            long retryAfterSeconds = Math.max(1, ((windowNumber + 1) * WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests; try again later\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(String prefix, String remoteAddress, UserPrincipal user) {
        return user == null ? prefix + remoteAddress : prefix + user.userId();
    }

    private UserPrincipal authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private boolean tryAcquire(String key, int limit, long windowNumber) {
        if (requests.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            removeExpiredWindows(windowNumber);
        }

        Window window = windows.get(key);
        if (window == null) {
            synchronized (windows) {
                window = windows.get(key);
                if (window == null) {
                    if (windows.size() >= MAX_TRACKED_CLIENTS) {
                        return false;
                    }
                    window = new Window(windowNumber);
                    windows.put(key, window);
                }
            }
        }

        synchronized (window) {
            if (window.number != windowNumber) {
                window.number = windowNumber;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void removeExpiredWindows(long currentWindow) {
        synchronized (windows) {
            windows.entrySet().removeIf(entry -> entry.getValue().number < currentWindow - 1);
        }
    }

    private static final class Window {
        private long number;
        private int count;

        private Window(long number) {
            this.number = number;
        }
    }
}
