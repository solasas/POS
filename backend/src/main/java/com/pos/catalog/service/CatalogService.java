package com.pos.catalog.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.catalog.dto.CategoryRequest;
import com.pos.catalog.dto.CategoryResponse;
import com.pos.catalog.dto.ProductRequest;
import com.pos.catalog.dto.ProductResponse;
import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.exception.CatalogConflictException;
import com.pos.catalog.exception.CatalogEntryNotFoundException;
import com.pos.catalog.exception.CatalogValidationException;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ---- categories ----

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new CatalogConflictException("Category name already exists: " + name);
        }
        return CategoryResponse.from(categoryRepository.save(Category.builder().name(name).build()));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CatalogConflictException("Category name already exists: " + name);
        }
        category.setName(name);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new CatalogConflictException("Category still has products and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    // ---- products ----

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productRepository.findAllWithCategory().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String sku = request.sku().trim();
        String barcode = normalizeBarcode(request.barcode());
        if (productRepository.existsBySku(sku)) {
            throw new CatalogConflictException("SKU already exists: " + sku);
        }
        if (barcode != null && productRepository.existsByBarcode(barcode)) {
            throw new CatalogConflictException("Barcode already exists: " + barcode);
        }

        Product product = Product.builder().build();
        apply(product, request, sku, barcode);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        String sku = request.sku().trim();
        String barcode = normalizeBarcode(request.barcode());
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new CatalogConflictException("SKU already exists: " + sku);
        }
        if (barcode != null && productRepository.existsByBarcodeAndIdNot(barcode, id)) {
            throw new CatalogConflictException("Barcode already exists: " + barcode);
        }

        apply(product, request, sku, barcode);
        return ProductResponse.from(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProduct(id);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new CatalogConflictException("Product is referenced by sales or inventory and cannot be deleted");
        }
    }

    private void apply(Product product, ProductRequest request, String sku, String barcode) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CatalogValidationException("categoryId: category not found"));
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setName(request.name().trim());
        product.setCategory(category);
        product.setUnitType(request.unitType());
        product.setPrice(request.price());
        product.setTaxClass(request.taxClass().trim());
        product.setAgeRestricted(request.ageRestricted());
        product.setReorderPoint(request.reorderPoint());
    }

    /** A blank barcode means "none"; storing "" would collide on the unique constraint. */
    private static String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim();
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CatalogEntryNotFoundException("Category", id));
    }

    private Product findProduct(UUID id) {
        return productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new CatalogEntryNotFoundException("Product", id));
    }
}
