package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.List;

import com.pos.sales.entity.PaymentMethod;

/**
 * Aggregate of COMPLETED sales in a period (voided sales are excluded). {@code costTotal} and
 * {@code profit} are only filled in for MANAGER and ADMIN; cashiers never see cost or margin.
 */
public record SalesSummaryResponse(
        long saleCount,
        BigDecimal grandTotal,
        List<MethodTotal> byPaymentMethod,
        BigDecimal costTotal,
        BigDecimal profit) {

    public record MethodTotal(PaymentMethod method, long saleCount, BigDecimal grandTotal) {
    }
}
