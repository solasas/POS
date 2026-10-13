package com.pos.inventory.exception;

import org.springframework.http.HttpStatus;

public class InventoryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;

    public InventoryException(HttpStatus status, String code, String message, String field) {
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

    public static InventoryException productNotFound(Object id) {
        return new InventoryException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found: " + id, "productId");
    }

    public static InventoryException wouldGoNegative(String name, String available, String change) {
        return new InventoryException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Stock for '" + name + "' would go negative: on hand " + available + ", change " + change,
                "quantityChange");
    }

    public static InventoryException invalidQuantity(String message) {
        return new InventoryException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", message, "quantityChange");
    }
}
