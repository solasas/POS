package com.pos.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.pos.catalog.entity.Product;

/** Composable filters behind {@code GET /api/products}. Null arguments mean "no filter". */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Case-insensitive "contains" match on name, SKU or barcode. */
    public static Specification<Product> matchesSearch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(term.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                cb.like(cb.lower(root.get("barcode")), pattern, '\\'));
    }

    public static Specification<Product> inCategory(UUID categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> hasActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** {@code true} keeps products at or below their threshold; {@code false} keeps the rest. */
    public static Specification<Product> lowStock(Boolean lowStock) {
        if (lowStock == null) {
            return null;
        }
        return (root, query, cb) -> lowStock
                ? cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("lowStockThreshold"))
                : cb.greaterThan(root.get("stockQuantity"), root.get("lowStockThreshold"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
