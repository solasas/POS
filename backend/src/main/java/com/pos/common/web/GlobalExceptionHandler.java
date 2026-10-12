package com.pos.common.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler({InvalidTransactionStateException.class, CatalogConflictException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CatalogValidationException.class)
    public ResponseEntity<ApiError> handleCatalogValidation(CatalogValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
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
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
