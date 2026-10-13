package com.pos.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.Sale;
import com.pos.sales.entity.SaleStatus;

public record SaleSummaryResponse(
        UUID id,
        String saleNumber,
        Instant soldAt,
        UUID cashierId,
        String cashierName,
        int itemCount,
        BigDecimal grandTotal,
        PaymentMethod paymentMethod,
        SaleStatus status) {

    public static SaleSummaryResponse from(Sale sale) {
        return new SaleSummaryResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getSoldAt(),
                sale.getCashierId(),
                sale.getCashierName(),
                sale.getItemCount(),
                sale.getGrandTotal(),
                sale.getPaymentMethod(),
                sale.getStatus());
    }
}
