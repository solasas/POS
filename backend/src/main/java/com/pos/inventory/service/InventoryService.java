package com.pos.inventory.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.dto.AdjustmentRequest;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.entity.InventoryTransaction;
import com.pos.inventory.entity.MovementType;
import com.pos.inventory.exception.InventoryException;
import com.pos.inventory.repository.InventoryTransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Manual stock changes. Stock is only ever changed by appending a ledger entry in the same
 * transaction, never by editing a product's quantity directly. Mistakes are fixed with another
 * (compensating) adjustment, never by editing history.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository ledger;

    @Transactional
    public InventoryTransactionResponse adjust(AuthenticatedUser actor, AdjustmentRequest request) {
        BigDecimal change = request.quantityChange();
        if (change.signum() == 0) {
            throw InventoryException.invalidQuantity("Quantity change must not be zero");
        }

        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> InventoryException.productNotFound(request.productId()));

        if (product.getUnit().requiresWholeQuantities() && change.remainder(BigDecimal.ONE).signum() != 0) {
            throw InventoryException.invalidQuantity(
                    "'" + product.getName() + "' is counted in whole units; use a whole-number quantity");
        }

        BigDecimal before = product.getStockQuantity();
        BigDecimal after = before.add(change);
        if (after.signum() < 0) {
            throw InventoryException.wouldGoNegative(product.getName(),
                    before.stripTrailingZeros().toPlainString(), change.stripTrailingZeros().toPlainString());
        }

        product.setStockQuantity(after);
        InventoryTransaction saved = ledger.saveAndFlush(InventoryTransaction.builder()
                .product(product)
                .movementType(MovementType.ADJUSTMENT)
                .quantityChange(change)
                .stockBefore(before)
                .stockAfter(after)
                .reason(request.reason().trim())
                .actorId(actor.id())
                .build());

        log.info("inventory.adjusted product={} change={} before={} after={} actor={}",
                product.getId(), change, before, after, actor.id());
        return InventoryTransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> history(UUID productId, UUID saleId, Pageable pageable) {
        Specification<InventoryTransaction> spec = (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("product");
            }
            return cb.conjunction();
        };
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        if (saleId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("saleId"), saleId));
        }
        return ledger.findAll(spec, pageable).map(InventoryTransactionResponse::from);
    }
}
