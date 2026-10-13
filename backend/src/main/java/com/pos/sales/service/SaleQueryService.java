package com.pos.sales.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.entity.Role;
import com.pos.auth.security.AuthenticatedUser;
import com.pos.sales.config.StoreProperties;
import com.pos.sales.dto.ReceiptResponse;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.dto.SaleSummaryResponse;
import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.Sale;
import com.pos.sales.entity.SaleStatus;
import com.pos.sales.exception.SaleException;
import com.pos.sales.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sale reads with ownership rules. CASHIERs can only see sales they rang up; MANAGER and ADMIN see
 * all. A sale a cashier may not see is reported as "not found" rather than "forbidden" so sale
 * numbers cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class SaleQueryService {

    private final SaleRepository saleRepository;
    private final StoreProperties storeProperties;

    public record Filter(String saleNumber, Instant from, Instant to, UUID cashierId,
                         PaymentMethod paymentMethod, SaleStatus status) {
    }

    /** All sales (managers/admins) or, with {@code onlyMine}, just the caller's. */
    @Transactional(readOnly = true)
    public Page<SaleSummaryResponse> list(AuthenticatedUser actor, Filter filter, boolean onlyMine, Pageable pageable) {
        boolean restrictToSelf = onlyMine || actor.role() == Role.CASHIER;

        Specification<Sale> spec = (root, query, cb) -> cb.conjunction();
        if (restrictToSelf) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("cashierId"), actor.id()));
        } else if (filter.cashierId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("cashierId"), filter.cashierId()));
        }
        if (filter.saleNumber() != null && !filter.saleNumber().isBlank()) {
            String pattern = "%" + filter.saleNumber().trim().toLowerCase()
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("saleNumber")), pattern, '\\'));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("soldAt"), filter.from()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("soldAt"), filter.to()));
        }
        if (filter.paymentMethod() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
        }
        if (filter.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        return saleRepository.findAll(spec, pageable).map(SaleSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(AuthenticatedUser actor, UUID id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> SaleException.saleNotFound(id.toString()));
        return SaleResponse.from(authorize(actor, sale));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(AuthenticatedUser actor, String saleNumber) {
        Sale sale = saleRepository.findDetailedBySaleNumber(saleNumber.trim())
                .orElseThrow(() -> SaleException.saleNotFound(saleNumber));
        return ReceiptResponse.of(storeProperties, SaleResponse.from(authorize(actor, sale)));
    }

    private Sale authorize(AuthenticatedUser actor, Sale sale) {
        boolean privileged = actor.role() == Role.MANAGER || actor.role() == Role.ADMIN;
        if (!privileged && !sale.getCashierId().equals(actor.id())) {
            throw SaleException.saleNotFound(sale.getSaleNumber());
        }
        return sale;
    }
}
