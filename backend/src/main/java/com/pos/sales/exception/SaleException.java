package com.pos.sales.exception;

import org.springframework.http.HttpStatus;

/**
 * A checkout or sales-query failure that maps to a specific HTTP status, machine-readable code and
 * (optionally) a request field. Messages are safe to show to the cashier.
 */
public class SaleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;

    public SaleException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public static SaleException productNotFound(Object productId) {
        return new SaleException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                "Product not found: " + productId, "items." + productId);
    }

    public static SaleException productInactive(String name, Object productId) {
        return new SaleException(HttpStatus.CONFLICT, "PRODUCT_INACTIVE",
                "'" + name + "' is inactive and cannot be sold", "items." + productId);
    }

    public static SaleException insufficientStock(String name, Object productId, String requested, String available) {
        return new SaleException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Not enough stock for '" + name + "': requested " + requested + ", available " + available,
                "items." + productId);
    }

    public static SaleException invalidQuantity(String message, Object productId) {
        return new SaleException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", message, "items." + productId);
    }

    public static SaleException duplicateLine(Object productId) {
        return new SaleException(HttpStatus.BAD_REQUEST, "DUPLICATE_LINE",
                "The same product appears more than once; combine it into one line", "items." + productId);
    }

    public static SaleException invalidPayment(String message, String field) {
        return new SaleException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT", message, field);
    }

    public static SaleException discountNotPermitted(String message) {
        return new SaleException(HttpStatus.FORBIDDEN, "DISCOUNT_NOT_PERMITTED", message, "discountPercent");
    }

    public static SaleException idempotencyKeyReused() {
        return new SaleException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different checkout request", null);
    }

    public static SaleException invalidIdempotencyKey() {
        return new SaleException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key header is required (1-64 characters: letters, digits, '-' or '_')", null);
    }

    public static SaleException saleNotFound(String identifier) {
        return new SaleException(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Sale not found: " + identifier, null);
    }
}
