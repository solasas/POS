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

import com.pos.catalog.dto.CategoryRequest;
import com.pos.catalog.dto.CategoryResponse;
import com.pos.catalog.service.CatalogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalog/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasRole('CASHIER')")
    public List<CategoryResponse> list() {
        return catalogService.listCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void delete(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
    }
}
