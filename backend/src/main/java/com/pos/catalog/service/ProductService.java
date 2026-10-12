package com.pos.catalog.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.catalog.dto.ProductRequest;
import com.pos.catalog.dto.ProductResponse;
import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.exception.CatalogConflictException;
import com.pos.catalog.exception.CatalogEntryNotFoundException;
import com.pos.catalog.exception.CatalogValidationException;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.catalog.repository.ProductSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(
            String search, UUID categoryId, Boolean active, Boolean lowStock, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.matchesSearch(search),
                ProductSpecifications.inCategory(categoryId),
                ProductSpecifications.hasActive(active),
                ProductSpecifications.lowStock(lowStock));
        return productRepository.findAll(spec, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode) {
        return productRepository.findByBarcodeWithCategory(barcode.trim())
                .map(ProductResponse::from)
                .orElseThrow(() -> new CatalogEntryNotFoundException("Product with barcode", barcode));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        String barcode = normalizeBarcode(request.barcode());
        if (productRepository.existsBySku(sku)) {
            throw new CatalogConflictException("sku", "SKU '" + sku + "' is already in use");
        }
        if (barcode != null && productRepository.existsByBarcode(barcode)) {
            throw new CatalogConflictException("barcode", "Barcode '" + barcode + "' is already in use");
        }

        Product product = Product.builder().build();
        apply(product, request, sku, barcode);
        // stock starts at zero by construction (Product default); only inventory features change it
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        String sku = normalizeSku(request.sku());
        String barcode = normalizeBarcode(request.barcode());
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new CatalogConflictException("sku", "SKU '" + sku + "' is already in use");
        }
        if (barcode != null && productRepository.existsByBarcodeAndIdNot(barcode, id)) {
            throw new CatalogConflictException("barcode", "Barcode '" + barcode + "' is already in use");
        }

        apply(product, request, sku, barcode);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse setActive(UUID id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        return ProductResponse.from(product);
    }

    private void apply(Product product, ProductRequest request, String sku, String barcode) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CatalogValidationException("categoryId", "Category does not exist"));
        // allow keeping a product in its current category even if that category was deactivated
        boolean changingCategory = product.getCategory() == null
                || !product.getCategory().getId().equals(category.getId());
        if (!category.isActive() && changingCategory) {
            throw new CatalogValidationException("categoryId", "Category is inactive");
        }

        product.setSku(sku);
        product.setBarcode(barcode);
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setCategory(category);
        product.setUnit(request.unit());
        product.setPrice(request.price());
        product.setCostPrice(request.costPrice());
        product.setLowStockThreshold(request.lowStockThreshold());
    }

    private Product find(UUID id) {
        return productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new CatalogEntryNotFoundException("Product", id));
    }

    /** SKUs are case-insensitive in practice, so store them upper-cased to make uniqueness reliable. */
    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    /** A blank barcode means "none"; storing "" would collide on the unique constraint. */
    private static String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
