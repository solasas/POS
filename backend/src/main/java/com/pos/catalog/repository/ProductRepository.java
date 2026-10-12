package com.pos.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pos.catalog.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select p from Product p join fetch p.category order by p.name")
    List<Product> findAllWithCategory();

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findByIdWithCategory(UUID id);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, UUID id);

    boolean existsByCategoryId(UUID categoryId);
}
