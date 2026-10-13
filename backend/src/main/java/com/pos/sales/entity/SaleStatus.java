package com.pos.sales.entity;

/** A sale is created already COMPLETED. VOIDED is reserved; no endpoint sets it in this milestone. */
public enum SaleStatus {
    COMPLETED,
    VOIDED
}
