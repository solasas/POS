package com.pos.sales.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.sales.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
