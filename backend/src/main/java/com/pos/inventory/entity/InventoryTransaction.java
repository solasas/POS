package com.pos.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.pos.catalog.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One append-only stock movement. Never updated or deleted; corrections are new entries. */
@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 16)
    private MovementType movementType;

    @Column(name = "quantity_change", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantityChange;

    @Column(name = "stock_before", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stockAfter;

    @Column(name = "sale_id", updatable = false)
    private UUID saleId;

    @Column(updatable = false)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
