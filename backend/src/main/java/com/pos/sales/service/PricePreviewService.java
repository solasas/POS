package com.pos.sales.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.sales.config.PricingProperties;
import com.pos.sales.dto.CheckoutItem;
import com.pos.sales.dto.PreviewRequest;
import com.pos.sales.dto.PricePreviewResponse;
import com.pos.sales.exception.SaleException;

import lombok.RequiredArgsConstructor;

/**
 * Prices a basket with the same engine as checkout so the cashier sees authoritative totals before
 * paying. It reads but never writes: no sale, no stock hold, no lock.
 */
@Service
@RequiredArgsConstructor
public class PricePreviewService {

    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final PricingProperties pricingProperties;

    @Transactional(readOnly = true)
    public PricePreviewResponse preview(AuthenticatedUser actor, PreviewRequest request) {
        Set<UUID> seen = new HashSet<>();
        for (CheckoutItem item : request.items()) {
            if (!seen.add(item.productId())) {
                throw SaleException.duplicateLine(item.productId());
            }
        }
        BigDecimal percent = request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent();
        BigDecimal max = pricingProperties.discount().maxFor(actor.role());
        if (percent.compareTo(max) > 0) {
            throw SaleException.discountNotPermitted(
                    "A " + actor.role() + " may apply at most " + max.stripTrailingZeros().toPlainString()
                            + "% discount");
        }

        Map<UUID, Product> products = productRepository.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CheckoutItem> ordered = request.items().stream()
                .sorted(Comparator.comparing(CheckoutItem::productId)).toList();
        List<PricingService.Input> inputs = new ArrayList<>();
        for (CheckoutItem item : ordered) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw SaleException.productNotFound(item.productId());
            }
            inputs.add(new PricingService.Input(
                    product.getId(), product.getTaxClass(), product.getPrice(), item.quantity()));
        }

        PricingService.Result result = pricingService.price(inputs, percent);
        return new PricePreviewResponse(
                result.lines().stream()
                        .map(l -> new PricePreviewResponse.Line(l.productId(), l.quantity(), l.unitPrice(),
                                l.lineSubtotal(), l.discountAmount(), l.taxRatePercent(), l.taxAmount(),
                                l.lineTotal()))
                        .toList(),
                result.subtotal(), result.discountTotal(), result.taxTotal(), result.grandTotal(),
                result.pricesIncludeTax(), max);
    }
}
