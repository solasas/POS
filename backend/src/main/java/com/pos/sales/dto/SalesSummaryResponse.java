package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.List;

import com.pos.sales.entity.PaymentMethod;

/** Aggregate of COMPLETED sales in a period (voided sales are excluded). */
public record SalesSummaryResponse(long saleCount, BigDecimal grandTotal, List<MethodTotal> byPaymentMethod) {

    public record MethodTotal(PaymentMethod method, long saleCount, BigDecimal grandTotal) {
    }
}
