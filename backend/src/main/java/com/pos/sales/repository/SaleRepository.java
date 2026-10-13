package com.pos.sales.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.pos.sales.entity.Sale;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {

    @EntityGraph(attributePaths = "items")
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "items")
    @Query("select s from Sale s where s.saleNumber = :saleNumber")
    Optional<Sale> findDetailedBySaleNumber(String saleNumber);

    @EntityGraph(attributePaths = "items")
    @Query("select s from Sale s where s.idempotencyKey = :key")
    Optional<Sale> findDetailedByIdempotencyKey(String key);

    /** Next value of the sale-number sequence (gaps are fine; uniqueness is what matters). */
    @Query(value = "select nextval('sale_number_seq')", nativeQuery = true)
    long nextSaleSequence();
}
