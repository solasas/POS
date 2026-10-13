package com.pos.inventory.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.pos.inventory.entity.InventoryTransaction;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, UUID>, JpaSpecificationExecutor<InventoryTransaction> {

    List<InventoryTransaction> findAllBySaleIdOrderByCreatedAtAsc(UUID saleId);
}
