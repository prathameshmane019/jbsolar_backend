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
    ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", Instant.now(), "error", "record_has_dependencies",
                "message", "The record cannot be permanently deleted because related data still references it"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now(), "error", "internal_error", "message", "An unexpected error occurred"));
    }
}
