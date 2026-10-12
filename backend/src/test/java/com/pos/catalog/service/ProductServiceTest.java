package com.pos.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pos.catalog.dto.ProductRequest;
import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.catalog.exception.CatalogConflictException;
import com.pos.catalog.exception.CatalogValidationException;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ProductService service;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository);
        category = Category.builder().id(UUID.randomUUID()).name("Dairy").active(true).build();
    }

    private ProductRequest request(String sku, String barcode) {
        return new ProductRequest(sku, barcode, "  Whole Milk  ", " 1L carton ", category.getId(),
                UnitOfMeasure.LITRE, new BigDecimal("1.99"), new BigDecimal("1.10"), new BigDecimal("5"));
    }

    @Test
    void createNormalizesInputAndStartsWithZeroStock() {
        when(productRepository.existsBySku("MILK-1")).thenReturn(false);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        service.create(request("  milk-1 ", "   "));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        Product product = saved.getValue();
        assertThat(product.getSku()).isEqualTo("MILK-1");
        assertThat(product.getBarcode()).isNull();
        assertThat(product.getName()).isEqualTo("Whole Milk");
        assertThat(product.getDescription()).isEqualTo("1L carton");
        assertThat(product.getStockQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void createRejectsADuplicateSkuBeforeTouchingTheDatabase() {
        when(productRepository.existsBySku("MILK-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("milk-1", null)))
                .isInstanceOfSatisfying(CatalogConflictException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("sku"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsADuplicateBarcode() {
        when(productRepository.existsBySku("MILK-1")).thenReturn(false);
        when(productRepository.existsByBarcode("123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("MILK-1", "123")))
                .isInstanceOfSatisfying(CatalogConflictException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("barcode"));
    }

    @Test
    void createRejectsAMissingCategory() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("MILK-1", null)))
                .isInstanceOfSatisfying(CatalogValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("categoryId"));
    }

    @Test
    void createRejectsAnInactiveCategory() {
        category.setActive(false);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.create(request("MILK-1", null)))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void updateKeepsAProductInItsCategoryEvenAfterTheCategoryWasDeactivated() {
        category.setActive(false);
        Product existing = Product.builder().id(UUID.randomUUID()).sku("OLD").name("Old")
                .category(category).unit(UnitOfMeasure.PIECE).price(BigDecimal.ONE).build();
        when(productRepository.findByIdWithCategory(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        service.update(existing.getId(), request("new", null));

        assertThat(existing.getSku()).isEqualTo("NEW");
        assertThat(existing.getUnit()).isEqualTo(UnitOfMeasure.LITRE);
    }

    @Test
    void lowStockIsDerivedFromStockAndThreshold() {
        Product product = Product.builder()
                .stockQuantity(new BigDecimal("5")).lowStockThreshold(new BigDecimal("5")).build();
        assertThat(product.isLowStock()).isTrue();

        product.setStockQuantity(new BigDecimal("5.001"));
        assertThat(product.isLowStock()).isFalse();
    }
}
