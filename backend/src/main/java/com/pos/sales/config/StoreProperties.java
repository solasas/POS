package com.pos.sales.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Store details printed on receipts, bound from {@code pos.store.*}. */
@ConfigurationProperties(prefix = "pos.store")
public record StoreProperties(String name, String address, String phone, String taxId, String footer) {

    public StoreProperties {
        name = name == null || name.isBlank() ? "Stockora Supermarket" : name;
        address = address == null ? "" : address;
        phone = phone == null ? "" : phone;
        taxId = taxId == null ? "" : taxId;
        footer = footer == null || footer.isBlank() ? "Thank you for shopping with us!" : footer;
    }
}
