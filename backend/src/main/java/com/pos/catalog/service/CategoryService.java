package com.pos.catalog.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.catalog.dto.CategoryRequest;
import com.pos.catalog.dto.CategoryResponse;
import com.pos.catalog.entity.Category;
import com.pos.catalog.exception.CatalogConflictException;
import com.pos.catalog.exception.CatalogEntryNotFoundException;
import com.pos.catalog.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Category> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return categoryRepository.findAll(spec, pageable).map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new CatalogConflictException("name", "A category named '" + name + "' already exists");
        }
        Category category = Category.builder()
                .name(name)
                .description(blankToNull(request.description()))
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CatalogConflictException("name", "A category named '" + name + "' already exists");
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse setActive(UUID id, boolean active) {
        Category category = find(id);
        category.setActive(active);
        return CategoryResponse.from(category);
    }

    private Category find(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CatalogEntryNotFoundException("Category", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
