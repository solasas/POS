package com.pos.catalog.entity;

/** How a product is sold and counted. Mirrors the chk_products_unit constraint. */
public enum UnitOfMeasure {
    PIECE,
    KG,
    GRAM,
    LITRE,
    MILLILITRE,
    PACKET,
    BOX,
    DOZEN
}
