package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Server-calculated figures for a basket; nothing is persisted and no stock is held. */
public record PricePreviewResponse(
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        boolean pricesIncludeTax,
        BigDecimal maxDiscountPercent) {

    public record Line(
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }
}
