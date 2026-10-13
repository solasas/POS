package com.pos.sales.entity;

/** Payment methods a cashier can record. No gateway is integrated: these are cashier-confirmed. */
public enum PaymentMethod {
    CASH,
    UPI,
    CARD
}
