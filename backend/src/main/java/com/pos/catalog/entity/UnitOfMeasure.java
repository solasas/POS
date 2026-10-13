package com.pos.catalog.entity;

/** How a product is sold and counted. Mirrors the chk_products_unit constraint. */
public enum UnitOfMeasure {
    PIECE(true),
    KG(false),
    GRAM(false),
    LITRE(false),
    MILLILITRE(false),
    PACKET(true),
    BOX(true),
    DOZEN(true);

    private final boolean wholeQuantities;

    UnitOfMeasure(boolean wholeQuantities) {
        this.wholeQuantities = wholeQuantities;
    }

    /** True for countable units, which can only be sold or stocked in whole numbers. */
    public boolean requiresWholeQuantities() {
        return wholeQuantities;
    }
}
