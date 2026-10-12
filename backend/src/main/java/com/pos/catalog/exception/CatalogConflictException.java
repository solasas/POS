package com.pos.catalog.exception;

public class CatalogConflictException extends RuntimeException {

    private final String field;

    public CatalogConflictException(String message) {
        this(null, message);
    }

    /** @param field the request field the conflict belongs to, so clients can show a field-level error */
    public CatalogConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
