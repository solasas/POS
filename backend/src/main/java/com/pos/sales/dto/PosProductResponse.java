package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;

/** What a cashier needs to ring a product up. {@code available} reflects stock and active status. */
public record PosProductResponse(
        UUID id,
        String name,
        String sku,
        String barcode,
        String categoryName,
        UnitOfMeasure unit,
        boolean wholeQuantitiesOnly,
        BigDecimal price,
        BigDecimal stockQuantity,
        boolean active,
        boolean available) {

    public static PosProductResponse from(Product product) {
        return new PosProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getBarcode(),
                product.getCategory().getName(),
                product.getUnit(),
                product.getUnit().requiresWholeQuantities(),
                product.getPrice(),
                product.getStockQuantity(),
                product.isActive(),
                product.isActive() && product.getStockQuantity().signum() > 0);
    }
}
