package com.pos.catalog.exception;

public class CatalogEntryNotFoundException extends RuntimeException {

    public CatalogEntryNotFoundException(String kind, Object identifier) {
        super(kind + " not found: " + identifier);
    }
}
