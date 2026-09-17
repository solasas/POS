package com.pos.pricing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.pos.catalog.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "old_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newPrice;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
