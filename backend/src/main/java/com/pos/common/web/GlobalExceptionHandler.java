package com.pos.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.pos.auth.exception.InvalidCredentialsException;
import com.pos.auth.exception.NoActiveRegisterSessionException;
import com.pos.auth.exception.RegisterNotActiveException;
import com.pos.catalog.exception.CatalogConflictException;
import com.pos.catalog.exception.CatalogEntryNotFoundException;
import com.pos.catalog.exception.CatalogValidationException;
import com.pos.inventory.exception.InventoryException;
import com.pos.sales.exception.SaleException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InvalidCredentialsException.class, RegisterNotActiveException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NoActiveRegisterSessionException.class)
    public ResponseEntity<ApiError> handleBadRequest(NoActiveRegisterSessionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CatalogEntryNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CatalogEntryNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SaleException.class)
    public ResponseEntity<ApiError> handleSale(SaleException ex) {
        Map<String, String> fieldErrors = ex.getField() == null ? null : Map.of(ex.getField(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(
                ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getMessage(), ex.getCode(), fieldErrors));
    }

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ApiError> handleInventory(InventoryException ex) {
        Map<String, String> fieldErrors = ex.getField() == null ? null : Map.of(ex.getField(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(
                ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getMessage(), ex.getCode(), fieldErrors));
    }

    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<ApiError> handleCatalogConflict(CatalogConflictException ex) {
        return buildFieldError(HttpStatus.CONFLICT, ex.getMessage(), ex.getField());
    }

    @ExceptionHandler(CatalogValidationException.class)
    public ResponseEntity<ApiError> handleCatalogValidation(CatalogValidationException ex) {
        return buildFieldError(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getField());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildFieldError(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", ex.getName());
    }

    /** Backstop for races that slip past the service-level uniqueness checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = fieldErrors.entrySet().stream()
                .findFirst()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .orElse("Validation failed");
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message, fieldErrors));
    }

    /**
     * Last resort: log the cause server-side and return a generic body, so stack traces and
     * database details never reach the client. Framework exceptions that already carry an HTTP
     * status (405, 404, 415, ...) keep it.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase());
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<ApiError> buildFieldError(HttpStatus status, String message, String field) {
        Map<String, String> fieldErrors = field == null ? null : Map.of(field, message);
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message, fieldErrors));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
