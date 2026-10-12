package com.pos.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.common.entity.AuditLog;
import com.pos.common.repository.AuditLogRepository;
import com.pos.sales.dto.DiscountRequest;
import com.pos.sales.entity.Transaction;
import com.pos.sales.entity.TransactionStatus;
import com.pos.sales.exception.InvalidTransactionStateException;
import com.pos.sales.exception.TransactionNotFoundException;
import com.pos.sales.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Demonstrates role-guarded, state-guarded transaction operations: voiding a completed sale and
 * approving a large discount both require SUPERVISOR+ (enforced via {@code @PreAuthorize} on the
 * controller); this service enforces the transaction-state invariants underneath.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public Transaction voidTransaction(UUID transactionId, AuthenticatedUser actor) {
        Transaction transaction = getOrThrow(transactionId);
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new InvalidTransactionStateException("Only a COMPLETED transaction can be voided");
        }
        transaction.setStatus(TransactionStatus.VOIDED);

        auditLogRepository.save(AuditLog.builder()
                .actorId(actor.id())
                .action("VOID_TRANSACTION")
                .entity("Transaction")
                .entityId(transaction.getId())
                .build());

        return transaction;
    }

    @Transactional
    public Transaction applyDiscount(UUID transactionId, DiscountRequest request, AuthenticatedUser actor) {
        Transaction transaction = getOrThrow(transactionId);
        if (transaction.getStatus() != TransactionStatus.OPEN && transaction.getStatus() != TransactionStatus.HELD) {
            throw new InvalidTransactionStateException("Discounts can only be applied to an OPEN or HELD transaction");
        }

        BigDecimal discountAmount = transaction.getSubtotal()
                .multiply(request.percentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        transaction.setDiscountTotal(discountAmount);
        transaction.setGrandTotal(transaction.getSubtotal().subtract(discountAmount).add(transaction.getTaxTotal()));

        auditLogRepository.save(AuditLog.builder()
                .actorId(actor.id())
                .action("APPLY_DISCOUNT")
                .entity("Transaction")
                .entityId(transaction.getId())
                .reasonCode(request.reasonCode())
                .build());

        return transaction;
    }

    private Transaction getOrThrow(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
