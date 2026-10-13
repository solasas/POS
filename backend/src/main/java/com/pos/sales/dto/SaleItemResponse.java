package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.sales.entity.SaleItem;

public record SaleItemResponse(
        UUID productId,
        String name,
        String sku,
        String barcode,
        UnitOfMeasure unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineSubtotal,
        BigDecimal discountAmount,
        BigDecimal taxRatePercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getProduct().getId(),
                item.getProductName(),
                item.getProductSku(),
                item.getProductBarcode(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineSubtotal(),
                item.getDiscountAmount(),
                item.getTaxRate(),
                item.getTaxAmount(),
                item.getLineTotal());
    }
}
