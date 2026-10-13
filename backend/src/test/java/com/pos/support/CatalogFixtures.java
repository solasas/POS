package com.pos.support;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;

/** Creates catalog rows with unique identifiers directly through the repositories. */
public class CatalogFixtures {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final Category category;

    public CatalogFixtures(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.category = categoryRepository.save(Category.builder().name("Fixture-" + token()).build());
    }

    public Product product(String price, String stock) {
        return product(price, stock, UnitOfMeasure.PIECE, true);
    }

    public Product product(String price, String stock, UnitOfMeasure unit, boolean active) {
        String suffix = token().toUpperCase();
        return productRepository.save(Product.builder()
                .sku("FX-" + suffix)
                .barcode("9" + suffix.replaceAll("[^0-9]", "7") + "1")
                .name("Fixture " + suffix)
                .category(category)
                .unit(unit)
                .price(new BigDecimal(price))
                .stockQuantity(new BigDecimal(stock))
                .active(active)
                .build());
    }

    public Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
