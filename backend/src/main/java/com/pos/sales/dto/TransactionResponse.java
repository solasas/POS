package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.sales.entity.Transaction;
import com.pos.sales.entity.TransactionStatus;

public record TransactionResponse(
        UUID id,
        TransactionStatus status,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal grandTotal) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getSubtotal(),
                transaction.getTaxTotal(),
                transaction.getDiscountTotal(),
                transaction.getGrandTotal());
    }
}
