package com.pos.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.pos.sales.entity.Sale;
import com.pos.sales.entity.SaleStatus;

/** Full sale detail: immutable item snapshots, the server-side pricing breakdown and the payment. */
public record SaleResponse(
        UUID id,
        String saleNumber,
        SaleStatus status,
        Instant soldAt,
        UUID cashierId,
        String cashierName,
        List<SaleItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountPercent,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        boolean pricesIncludeTax,
        PaymentResponse payment) {

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getStatus(),
                sale.getSoldAt(),
                sale.getCashierId(),
                sale.getCashierName(),
                sale.getItems().stream()
                        .sorted(Comparator.comparing(i -> i.getProductName().toLowerCase()))
                        .map(SaleItemResponse::from)
                        .toList(),
                sale.getSubtotal(),
                sale.getDiscountPercent(),
                sale.getDiscountTotal(),
                sale.getTaxTotal(),
                sale.getGrandTotal(),
                sale.isPricesIncludeTax(),
                sale.getPayments().stream().findFirst().map(PaymentResponse::from).orElse(null));
    }
}
