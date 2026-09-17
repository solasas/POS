package com.pos.registers.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;

    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash;

    @Column(name = "closing_cash", precision = 12, scale = 2)
    private BigDecimal closingCash;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
