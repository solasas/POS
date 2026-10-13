package com.pos.sales.dto;

import com.pos.sales.config.StoreProperties;

/** Everything a printed receipt needs; derived entirely from the stored sale and store config. */
public record ReceiptResponse(Store store, SaleResponse sale, String footer) {

    public record Store(String name, String address, String phone, String taxId) {
    }

    public static ReceiptResponse of(StoreProperties store, SaleResponse sale) {
        return new ReceiptResponse(
                new Store(store.name(), store.address(), store.phone(), store.taxId()), sale, store.footer());
    }
}
