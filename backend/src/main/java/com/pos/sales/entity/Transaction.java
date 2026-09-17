package com.pos.sales.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.pos.customers.entity.Customer;
import com.pos.registers.entity.Register;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TransactionItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
