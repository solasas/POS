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
        PaymentResponse payment,
        BigDecimal costTotal,
        BigDecimal profit) {

    /** Without cost or profit; used for cashiers and receipts. */
    public static SaleResponse from(Sale sale) {
        return from(sale, false);
    }

    /**
     * @param includeProfit adds cost and profit. Profit is net revenue (line total minus tax)
     *                      less quantity x the cost snapshotted at sale time.
     */
    public static SaleResponse from(Sale sale, boolean includeProfit) {
        BigDecimal cost = null;
        BigDecimal profit = null;
        if (includeProfit) {
            cost = BigDecimal.ZERO;
            BigDecimal netRevenue = BigDecimal.ZERO;
            for (var item : sale.getItems()) {
                cost = cost.add(item.getQuantity().multiply(item.getUnitCost()).setScale(2, java.math.RoundingMode.HALF_UP));
                netRevenue = netRevenue.add(item.getLineTotal().subtract(item.getTaxAmount()));
            }
            profit = netRevenue.subtract(cost);
        }
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
                sale.getPayments().stream().findFirst().map(PaymentResponse::from).orElse(null),
                cost,
                profit);
    }
}
