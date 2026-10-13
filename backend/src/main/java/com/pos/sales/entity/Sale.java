package com.pos.sales.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
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

/** A completed checkout. Monetary totals here are the server-calculated, authoritative figures. */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sale_number", nullable = false, unique = true, updatable = false, length = 40)
    private String saleNumber;

    /** Present only when the cashier is signed in to a register (PIN session). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id")
    private Register register;

    @Column(name = "cashier_id", nullable = false, updatable = false)
    private UUID cashierId;

    /** Cashier name at the time of sale, so receipts do not change if the user is renamed. */
    @Column(name = "cashier_name", nullable = false, updatable = false)
    private String cashierName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SaleStatus status;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal discountPercent;

    /** Whether unit prices on this sale already included tax; fixed at sale time so receipts never drift. */
    @Column(name = "prices_include_tax", nullable = false, updatable = false)
    private boolean pricesIncludeTax;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16, updatable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 16, updatable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    @Column(name = "sold_at", nullable = false, updatable = false)
    private Instant soldAt;

    @Builder.Default
    @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<SaleItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();

    /** Read-only count used by list views without loading every line. */
    @Formula("(select count(*) from sale_items si where si.sale_id = id)")
    private int itemCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
