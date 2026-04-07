package com.primetech.poc.identity.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
    log.warn("Entity not found: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(DuplicateEntityException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEntity(DuplicateEntityException ex, HttpServletRequest request) {
    log.warn("Duplicate entity: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(com.primetech.poc.identity.shared.exception.AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(com.primetech.poc.identity.shared.exception.AuthenticationException ex, HttpServletRequest request) {
    log.warn("Authentication failed: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleSpringAuthentication(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
    log.warn("Spring authentication failed: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "AUTHENTICATION_FAILED",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
    log.warn("Bad credentials: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "INVALID_CREDENTIALS",
            "Invalid email or password",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            "ACCESS_DENIED",
            "You do not have permission to access this resource",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
        errors.put(error.getField(), error.getDefaultMessage())
    );

    log.warn("Validation errors: {}", errors);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Validation failed",
            request.getRequestURI(),
            errors
        ));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
    Map<String, String> errors = new HashMap<>();
    ex.getConstraintViolations().forEach(violation ->
        errors.put(violation.getPropertyPath().toString(), violation.getMessage())
    );

    log.warn("Constraint violations: {}", errors);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "CONSTRAINT_VIOLATION",
            "Constraint violation",
            request.getRequestURI(),
            errors
        ));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    log.warn("Business exception: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error: ", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getRequestURI()
        ));
  }

  /**
   * Standard error response format
   */
  public record ErrorResponse(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path,
      Map<String, String> details
  ) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
      this(timestamp, status, error, message, path, null);
    }
  }
}
