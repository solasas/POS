package com.pos.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pos.inventory.entity.InventoryTransaction;
import com.pos.inventory.entity.MovementType;

public record InventoryTransactionResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        MovementType movementType,
        BigDecimal quantityChange,
        BigDecimal stockBefore,
        BigDecimal stockAfter,
        UUID saleId,
        String reason,
        UUID actorId,
        Instant createdAt) {

    public static InventoryTransactionResponse from(InventoryTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getProduct().getId(),
                tx.getProduct().getName(),
                tx.getProduct().getSku(),
                tx.getMovementType(),
                tx.getQuantityChange(),
                tx.getStockBefore(),
                tx.getStockAfter(),
                tx.getSaleId(),
                tx.getReason(),
                tx.getActorId(),
                tx.getCreatedAt());
    }
}
