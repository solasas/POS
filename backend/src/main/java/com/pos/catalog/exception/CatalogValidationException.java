package com.pos.catalog.exception;

public class CatalogValidationException extends RuntimeException {

    private final String field;

    public CatalogValidationException(String message) {
        this(null, message);
    }

    public CatalogValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
