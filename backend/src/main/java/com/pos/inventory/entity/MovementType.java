package com.pos.inventory.entity;

/**
 * Ledger convention: {@code quantityChange} is signed. SALE is always negative; ADJUSTMENT may be
 * positive (stock received, found) or negative (damage, shrinkage) and always carries a reason.
 */
public enum MovementType {
    SALE,
    ADJUSTMENT
}
