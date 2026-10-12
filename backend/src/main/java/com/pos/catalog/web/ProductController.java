package com.pos.catalog.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pos.catalog.dto.ProductRequest;
import com.pos.catalog.dto.ProductResponse;
import com.pos.catalog.dto.StatusRequest;
import com.pos.catalog.exception.CatalogValidationException;
import com.pos.catalog.service.ProductService;
import com.pos.common.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    /** API sort keys mapped to entity properties; anything else is rejected rather than passed to JPA. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "sku", "sku",
            "price", "price",
            "stock", "stockQuantity",
            "createdAt", "createdAt");

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Search, filter, sort and paginate products",
            description = "search matches name, SKU or barcode (contains, case-insensitive). "
                    + "sortBy: name, sku, price, stock, createdAt. size is capped at 100.")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String property = SORT_FIELDS.get(sortBy);
        if (property == null) {
            throw new CatalogValidationException("sortBy", "Must be one of " + SORT_FIELDS.keySet());
        }
        Sort.Direction dir = switch (direction.toLowerCase()) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new CatalogValidationException("direction", "Must be 'asc' or 'desc'");
        };
        // secondary key keeps pagination stable when the primary values tie
        Sort sort = Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return PageResponse.of(productService.list(search, categoryId, active, lowStock, pageable), p -> p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Get a product")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Look up a product by exact barcode")
    public ProductResponse getByBarcode(@PathVariable String barcode) {
        return productService.getByBarcode(barcode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a product (MANAGER+); stock is initialized to zero")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a product (MANAGER+); does not change stock")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Activate or deactivate a product (MANAGER+)")
    public ProductResponse setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return productService.setActive(id, request.active());
    }
}
