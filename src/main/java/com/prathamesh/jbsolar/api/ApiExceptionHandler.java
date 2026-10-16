package com.prathamesh.jbsolar.api;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "timestamp", Instant.now(), "error", exception.getCode(), "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> handleValidation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                error -> error.getField(), error -> error.getDefaultMessage(), (first, second) -> first));
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "error", "validation_failed", "fields", errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "timestamp", Instant.now(), "error", "forbidden", "message", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String cause = exception.getMostSpecificCause().getMessage();
        if (cause != null && cause.toLowerCase(java.util.Locale.ROOT).contains("aadhaar_hash")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "timestamp", Instant.now(), "error", "aadhaar_already_registered",
                    "message", "A farmer with this Aadhaar number is already registered"));
        }
        if (cause != null && cause.toLowerCase(java.util.Locale.ROOT).contains("mobile")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "timestamp", Instant.now(), "error", "mobile_already_registered",
                    "message", "A user with this mobile number already exists"));
        }
        logger.warn("Data integrity violation while processing {} {} (cause type: {})",
                request.getMethod(), request.getRequestURI(),
                exception.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", Instant.now(), "error", "data_integrity_violation",
                "message", "The request conflicts with existing data or a database constraint"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now(), "error", "internal_error", "message", "An unexpected error occurred"));
    }
}
