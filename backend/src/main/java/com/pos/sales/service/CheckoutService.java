package com.pos.sales.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.entity.InventoryTransaction;
import com.pos.inventory.entity.MovementType;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.registers.repository.RegisterRepository;
import com.pos.sales.config.PricingProperties;
import com.pos.sales.dto.CheckoutItem;
import com.pos.sales.dto.CheckoutRequest;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.entity.Payment;
import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.PaymentStatus;
import com.pos.sales.entity.Sale;
import com.pos.sales.entity.SaleItem;
import com.pos.sales.entity.SaleStatus;
import com.pos.sales.exception.SaleException;
import com.pos.sales.repository.SaleRepository;
import com.pos.settings.service.AppSettingsService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Completes a sale as ONE database transaction. Everything below either commits together or rolls
 * back together: sale, items, payment, stock deductions and ledger entries.
 *
 * <h2>Concurrency</h2>
 * Product rows are locked with {@code SELECT ... FOR UPDATE} in ascending id order before stock is
 * read. A second checkout for the same product blocks until the first commits, then reads the
 * already-decremented stock, so two cashiers cannot sell the last unit. The consistent lock order
 * prevents deadlocks between overlapping baskets, and the {@code stock_quantity >= 0} check
 * constraint is a final backstop.
 *
 * <h2>Idempotency</h2>
 * The client-supplied key is stored on the sale (unique index) with a hash of the request. A
 * transaction-scoped advisory lock on the key serializes concurrent attempts, so a retry either
 * finds the committed sale (replayed) or runs after the first attempt rolled back. Because the key
 * lives in the same transaction as the sale, a failed checkout never consumes it.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final DateTimeFormatter SALE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final RegisterRepository registerRepository;
    private final PricingService pricingService;
    private final PricingProperties pricingProperties;
    private final AppSettingsService settingsService;
    private final EntityManager entityManager;
    private final Clock clock;

    /** Result of a checkout call; {@code replayed} is true when an earlier identical request is returned. */
    public record Outcome(SaleResponse sale, boolean replayed) {
    }

    @Transactional
    public Outcome checkout(AuthenticatedUser actor, String idempotencyKey, CheckoutRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{1,64}")) {
            throw SaleException.invalidIdempotencyKey();
        }
        String requestHash = fingerprint(request);
        log.info("checkout.attempt key={} cashier={} lines={} method={}",
                idempotencyKey, actor.id(), request.items().size(), request.payment().method());

        try {
            return doCheckout(actor, idempotencyKey, requestHash, request);
        } catch (SaleException ex) {
            if ("INSUFFICIENT_STOCK".equals(ex.getCode()) || "PRODUCT_INACTIVE".equals(ex.getCode())) {
                log.warn("checkout.inventory_conflict key={} cashier={} code={} detail=\"{}\"",
                        idempotencyKey, actor.id(), ex.getCode(), ex.getMessage());
            } else {
                log.warn("checkout.rejected key={} cashier={} code={} detail=\"{}\"",
                        idempotencyKey, actor.id(), ex.getCode(), ex.getMessage());
            }
            throw ex;
        }
    }

    private Outcome doCheckout(AuthenticatedUser actor, String key, String requestHash, CheckoutRequest request) {
        // Serialize concurrent attempts that share a key (released automatically at commit/rollback).
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:k, 0)) as text)")
                .setParameter("k", key)
                .getSingleResult();

        Optional<Sale> existing = saleRepository.findDetailedByIdempotencyKey(key);
        if (existing.isPresent()) {
            Sale sale = existing.get();
            if (!sale.getRequestHash().equals(requestHash) || !sale.getCashierId().equals(actor.id())) {
                throw SaleException.idempotencyKeyReused();
            }
            log.info("checkout.idempotent_replay key={} sale={} cashier={}", key, sale.getSaleNumber(), actor.id());
            return new Outcome(SaleResponse.from(sale), true);
        }

        // ---- validate shape, reject duplicate lines ---------------------------------------
        Set<UUID> seen = new HashSet<>();
        for (CheckoutItem item : request.items()) {
            if (!seen.add(item.productId())) {
                throw SaleException.duplicateLine(item.productId());
            }
        }
        BigDecimal discountPercent = request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent();
        BigDecimal maxDiscount = pricingProperties.discount().maxFor(actor.role());
        if (discountPercent.compareTo(maxDiscount) > 0) {
            throw SaleException.discountNotPermitted(
                    "A " + actor.role() + " may apply at most " + maxDiscount.stripTrailingZeros().toPlainString()
                            + "% discount");
        }

        // read inside the transaction so one checkout sees one consistent value of the switch
        boolean allowNegativeStock = settingsService.isNegativeStockAllowed();

        // ---- lock and load authoritative products (ascending id) --------------------------
        List<UUID> ids = request.items().stream().map(CheckoutItem::productId).sorted().toList();
        Map<UUID, Product> products = productRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CheckoutItem> orderedItems = request.items().stream()
                .sorted(Comparator.comparing(CheckoutItem::productId)).toList();

        List<PricingService.Input> inputs = new ArrayList<>(orderedItems.size());
        for (CheckoutItem item : orderedItems) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw SaleException.productNotFound(item.productId());
            }
            if (!product.isActive()) {
                throw SaleException.productInactive(product.getName(), product.getId());
            }
            if (product.getUnit().requiresWholeQuantities() && !isWhole(item.quantity())) {
                throw SaleException.invalidQuantity(
                        "'" + product.getName() + "' is sold by the " + product.getUnit().name().toLowerCase()
                                + " and needs a whole-number quantity", product.getId());
            }
            if (!allowNegativeStock && product.getStockQuantity().compareTo(item.quantity()) < 0) {
                throw SaleException.insufficientStock(product.getName(), product.getId(),
                        plain(item.quantity()), plain(product.getStockQuantity()));
            }
            inputs.add(new PricingService.Input(
                    product.getId(), product.getTaxClass(), product.getPrice(), item.quantity()));
        }

        // ---- authoritative pricing --------------------------------------------------------
        PricingService.Result priced = pricingService.price(inputs, discountPercent);

        // ---- payment validation -----------------------------------------------------------
        PaymentMethod method = request.payment().method();
        BigDecimal tendered = request.payment().amountTendered();
        BigDecimal changeDue = null;
        if (method == PaymentMethod.CASH) {
            if (tendered == null) {
                throw SaleException.invalidPayment(
                        "Amount tendered is required for cash payments", "payment.amountTendered");
            }
            if (tendered.compareTo(priced.grandTotal()) < 0) {
                throw SaleException.invalidPayment("Amount tendered (" + tendered.toPlainString()
                        + ") is less than the amount payable (" + priced.grandTotal().toPlainString() + ")",
                        "payment.amountTendered");
            }
            changeDue = tendered.subtract(priced.grandTotal());
        } else if (tendered != null) {
            throw SaleException.invalidPayment(
                    "Amount tendered only applies to cash payments", "payment.amountTendered");
        }

        // ---- create sale, items, payment --------------------------------------------------
        Instant now = Instant.now(clock);
        long sequence = saleRepository.nextSaleSequence();
        Sale sale = Sale.builder()
                .saleNumber("S" + SALE_DATE.format(now) + "-" + String.format("%06d", sequence))
                .register(actor.registerId() == null ? null : registerRepository.getReferenceById(actor.registerId()))
                .cashierId(actor.id())
                .cashierName(actor.name())
                .status(SaleStatus.COMPLETED)
                .subtotal(priced.subtotal())
                .discountPercent(discountPercent)
                .discountTotal(priced.discountTotal())
                .taxTotal(priced.taxTotal())
                .grandTotal(priced.grandTotal())
                .pricesIncludeTax(priced.pricesIncludeTax())
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.RECORDED)
                .idempotencyKey(key)
                .requestHash(requestHash)
                .soldAt(now)
                .build();

        for (int i = 0; i < orderedItems.size(); i++) {
            Product product = products.get(orderedItems.get(i).productId());
            PricingService.Line line = priced.lines().get(i);
            sale.getItems().add(SaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .productBarcode(product.getBarcode())
                    .unit(product.getUnit())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .lineSubtotal(line.lineSubtotal())
                    .discountAmount(line.discountAmount())
                    .taxRate(line.taxRatePercent())
                    .taxAmount(line.taxAmount())
                    .lineTotal(line.lineTotal())
                    .build());
        }
        sale.getPayments().add(Payment.builder()
                .sale(sale)
                .method(method)
                .status(PaymentStatus.RECORDED)
                .amount(priced.grandTotal())
                .tendered(tendered)
                .changeDue(changeDue)
                .build());
        saleRepository.saveAndFlush(sale);

        // ---- deduct stock and append ledger entries (rows are already locked) ------------
        for (int i = 0; i < orderedItems.size(); i++) {
            Product product = products.get(orderedItems.get(i).productId());
            BigDecimal quantity = orderedItems.get(i).quantity();
            BigDecimal before = product.getStockQuantity();
            BigDecimal after = before.subtract(quantity);
            product.setStockQuantity(after);
            if (after.signum() < 0) {
                log.warn("checkout.sold_beyond_stock key={} product={} before={} after={} cashier={}",
                        key, product.getId(), before, after, actor.id());
            }

            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .product(product)
                    .movementType(MovementType.SALE)
                    .quantityChange(quantity.negate())
                    .stockBefore(before)
                    .stockAfter(after)
                    .saleId(sale.getId())
                    .actorId(actor.id())
                    .build());
        }
        productRepository.flush();
        inventoryTransactionRepository.flush();

        log.info("checkout.completed key={} sale={} cashier={} lines={} total={} method={}",
                key, sale.getSaleNumber(), actor.id(), orderedItems.size(), priced.grandTotal(), method);
        return new Outcome(SaleResponse.from(sale), false);
    }

    private static boolean isWhole(BigDecimal quantity) {
        return quantity.remainder(BigDecimal.ONE).signum() == 0;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * Canonical SHA-256 of the logical request (order of lines and number formatting do not
     * matter) so the same retry matches and a changed basket does not.
     */
    static String fingerprint(CheckoutRequest request) {
        StringBuilder canonical = new StringBuilder();
        request.items().stream()
                .sorted(Comparator.comparing(CheckoutItem::productId))
                .forEach(i -> canonical.append(i.productId()).append('=')
                        .append(plain(i.quantity())).append(';'));
        BigDecimal discount = request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent();
        canonical.append("discount=").append(plain(discount)).append(';');
        canonical.append("method=").append(request.payment().method()).append(';');
        BigDecimal tendered = request.payment().amountTendered();
        canonical.append("tendered=").append(tendered == null ? "-" : plain(tendered));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
