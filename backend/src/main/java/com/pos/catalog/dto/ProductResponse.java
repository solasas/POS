package com.pos.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitType;

public record ProductResponse(
        UUID id,
        String sku,
        String barcode,
        String name,
        UUID categoryId,
        String categoryName,
        UnitType unitType,
        BigDecimal price,
        String taxClass,
        boolean ageRestricted,
        int reorderPoint) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getUnitType(),
                product.getPrice(),
                product.getTaxClass(),
                product.isAgeRestricted(),
                product.getReorderPoint());
    }
}
