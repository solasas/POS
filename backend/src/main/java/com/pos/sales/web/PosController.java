package com.pos.sales.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.sales.dto.PosProductResponse;
import com.pos.sales.service.PosProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@Tag(name = "POS")
public class PosController {

    private final PosProductService posProductService;

    @GetMapping("/products")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Search active products by name, SKU or barcode (max 20 results)")
    public List<PosProductResponse> search(@RequestParam(defaultValue = "") String query) {
        return posProductService.search(query);
    }

    @GetMapping("/products/barcode/{barcode}")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Exact barcode (or SKU) lookup",
            description = "Also returns inactive products (active=false) so the UI can explain why they cannot be sold.")
    public PosProductResponse byBarcode(@PathVariable String barcode) {
        return posProductService.byBarcode(barcode);
    }
}
