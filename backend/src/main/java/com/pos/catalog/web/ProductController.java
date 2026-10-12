package com.pos.catalog.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pos.catalog.dto.ProductRequest;
import com.pos.catalog.dto.ProductResponse;
import com.pos.catalog.service.CatalogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final CatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasRole('CASHIER')")
    public List<ProductResponse> list() {
        return catalogService.listProducts();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CASHIER')")
    public ProductResponse get(@PathVariable UUID id) {
        return catalogService.getProduct(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void delete(@PathVariable UUID id) {
        catalogService.deleteProduct(id);
    }
}
