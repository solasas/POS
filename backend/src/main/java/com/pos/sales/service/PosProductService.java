package com.pos.sales.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.catalog.repository.ProductSpecifications;
import com.pos.sales.dto.PosProductResponse;
import com.pos.sales.exception.SaleException;

import lombok.RequiredArgsConstructor;

/** Read-only product lookups for the checkout screen. */
@Service
@RequiredArgsConstructor
public class PosProductService {

    private static final int SEARCH_LIMIT = 20;

    private final ProductRepository productRepository;

    /** Free-text search over active products by name, SKU or barcode. */
    @Transactional(readOnly = true)
    public List<PosProductResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.matchesSearch(query),
                ProductSpecifications.hasActive(true));
        var pageable = PageRequest.of(0, SEARCH_LIMIT, Sort.by("name").ascending().and(Sort.by("id")));
        return productRepository.findAll(spec, pageable).stream().map(PosProductResponse::from).toList();
    }

    /**
     * Exact barcode (or SKU) match. Returns inactive products too so the cashier gets an accurate
     * "this product is inactive" message instead of "unknown barcode".
     */
    @Transactional(readOnly = true)
    public PosProductResponse byBarcode(String code) {
        String trimmed = code.trim();
        List<Product> matches = productRepository.findByBarcodeOrSku(trimmed);
        if (matches.isEmpty()) {
            matches = productRepository.findByBarcodeOrSku(trimmed.toUpperCase());
        }
        // a barcode match wins over an accidental SKU collision
        return matches.stream()
                .sorted((a, b) -> Boolean.compare(trimmed.equals(b.getBarcode()), trimmed.equals(a.getBarcode())))
                .findFirst()
                .map(PosProductResponse::from)
                .orElseThrow(() -> new SaleException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "UNKNOWN_BARCODE", "No product matches '" + trimmed + "'", null));
    }
}
