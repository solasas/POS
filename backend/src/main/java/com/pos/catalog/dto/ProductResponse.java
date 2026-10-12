package com.pos.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;

public record ProductResponse(
        UUID id,
        String sku,
        String barcode,
        String name,
        String description,
        UUID categoryId,
        String categoryName,
        UnitOfMeasure unit,
        BigDecimal price,
        BigDecimal costPrice,
        BigDecimal stockQuantity,
        BigDecimal lowStockThreshold,
        boolean lowStock,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getDescription(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getUnit(),
                product.getPrice(),
                product.getCostPrice(),
                product.getStockQuantity(),
                product.getLowStockThreshold(),
                product.isLowStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
