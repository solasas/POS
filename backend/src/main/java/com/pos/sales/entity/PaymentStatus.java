package com.pos.sales.entity;

/**
 * RECORDED means "the cashier confirmed they received this payment". It is not provider-verified.
 * Further states (e.g. VERIFIED, REFUNDED) belong to a future payment-gateway milestone.
 */
public enum PaymentStatus {
    RECORDED
}
