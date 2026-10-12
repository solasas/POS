package com.pos.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.pos.sales.exception.InvalidTransactionStateException;
import com.pos.sales.exception.TransactionNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, RegisterNotActiveException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NoActiveRegisterSessionException.class)
    public ResponseEntity<ApiError> handleBadRequest(NoActiveRegisterSessionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({TransactionNotFoundException.class, CatalogEntryNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<ApiError> handleConflict(InvalidTransactionStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
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

    private ResponseEntity<ApiError> buildFieldError(HttpStatus status, String message, String field) {
        Map<String, String> fieldErrors = field == null ? null : Map.of(field, message);
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message, fieldErrors));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
