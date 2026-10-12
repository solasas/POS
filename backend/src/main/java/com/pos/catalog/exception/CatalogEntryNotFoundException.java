package com.pos.catalog.exception;

import java.util.UUID;

public class CatalogEntryNotFoundException extends RuntimeException {

    public CatalogEntryNotFoundException(String kind, UUID id) {
        super(kind + " not found: " + id);
    }
}
