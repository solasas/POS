package com.pos.sales.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import com.pos.sales.dto.SalesSummaryResponse;
import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.Sale;
import com.pos.sales.entity.SaleItem;
import com.pos.sales.entity.SaleStatus;
import com.pos.sales.exception.SaleException;
import com.pos.sales.repository.SaleRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
    private final EntityManager entityManager;

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

    /**
     * Count and total of COMPLETED sales in [from, to), split by payment method. Cashiers only
     * ever aggregate their own sales; managers and admins aggregate everything unless
     * {@code onlyMine} is set.
     */
    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(AuthenticatedUser actor, Instant from, Instant to, boolean onlyMine) {
        boolean restrictToSelf = onlyMine || actor.role() == Role.CASHIER;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Sale> root = query.from(Sale.class);

        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(root.get("status"), SaleStatus.COMPLETED));
        if (restrictToSelf) {
            where.add(cb.equal(root.get("cashierId"), actor.id()));
        }
        if (from != null) {
            where.add(cb.greaterThanOrEqualTo(root.get("soldAt"), from));
        }
        if (to != null) {
            where.add(cb.lessThan(root.get("soldAt"), to));
        }
        query.multiselect(root.get("paymentMethod"), cb.count(root), cb.sum(root.<BigDecimal>get("grandTotal")))
                .where(where.toArray(Predicate[]::new))
                .groupBy(root.get("paymentMethod"));

        List<SalesSummaryResponse.MethodTotal> byMethod = new ArrayList<>();
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            PaymentMethod method = (PaymentMethod) row[0];
            long rowCount = (Long) row[1];
            BigDecimal rowTotal = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            byMethod.add(new SalesSummaryResponse.MethodTotal(method, rowCount, rowTotal));
            count += rowCount;
            total = total.add(rowTotal);
        }
        byMethod.sort(Comparator.comparing(SalesSummaryResponse.MethodTotal::grandTotal).reversed());

        BigDecimal cost = null;
        BigDecimal profit = null;
        if (actor.role() == Role.MANAGER || actor.role() == Role.ADMIN) {
            // line-level rounding mirrors SaleResponse so the dashboard and a sale's detail agree
            CriteriaQuery<Object[]> costQuery = cb.createQuery(Object[].class);
            Root<SaleItem> item = costQuery.from(SaleItem.class);
            Join<SaleItem, Sale> sale = item.join("sale");
            List<Predicate> itemWhere = new ArrayList<>();
            itemWhere.add(cb.equal(sale.get("status"), SaleStatus.COMPLETED));
            if (restrictToSelf) {
                itemWhere.add(cb.equal(sale.get("cashierId"), actor.id()));
            }
            if (from != null) {
                itemWhere.add(cb.greaterThanOrEqualTo(sale.get("soldAt"), from));
            }
            if (to != null) {
                itemWhere.add(cb.lessThan(sale.get("soldAt"), to));
            }
            costQuery.multiselect(
                            cb.sum(cb.function("round", BigDecimal.class,
                                    cb.prod(item.<BigDecimal>get("quantity"), item.<BigDecimal>get("unitCost")),
                                    cb.literal(2))),
                            cb.sum(cb.diff(item.<BigDecimal>get("lineTotal"), item.<BigDecimal>get("taxAmount"))))
                    .where(itemWhere.toArray(Predicate[]::new));
            Object[] row = entityManager.createQuery(costQuery).getSingleResult();
            cost = row[0] == null ? BigDecimal.ZERO : (BigDecimal) row[0];
            BigDecimal netRevenue = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            profit = netRevenue.subtract(cost);
        }
        return new SalesSummaryResponse(count, total, byMethod, cost, profit);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(AuthenticatedUser actor, UUID id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> SaleException.saleNotFound(id.toString()));
        boolean privileged = actor.role() == Role.MANAGER || actor.role() == Role.ADMIN;
        return SaleResponse.from(authorize(actor, sale), privileged);
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
