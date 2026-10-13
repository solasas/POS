package com.pos.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.pos.sales.entity.Payment;
import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.PaymentStatus;

/** status RECORDED = confirmed by the cashier, not verified by a payment provider. */
public record PaymentResponse(
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal amountTendered,
        BigDecimal changeDue,
        Instant recordedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getTendered(),
                payment.getChangeDue(),
                payment.getCreatedAt());
    }
}
