package com.pos.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pos.sales.config.PricingProperties;

import lombok.RequiredArgsConstructor;

/**
 * Authoritative price, discount and tax calculation. Pure arithmetic: it never touches the
 * database, and the same instance prices both the preview and the real checkout so the two cannot
 * disagree.
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li>All amounts are {@link BigDecimal}; currency scale is 2, rounding is HALF_UP.</li>
 *   <li>{@code lineSubtotal = round(unitPrice x quantity)}.</li>
 *   <li>{@code discountTotal = round(sum(lineSubtotal) x discountPercent / 100)}, allocated to
 *       lines in proportion to {@code lineSubtotal}; the last line absorbs the rounding remainder so
 *       the lines always add up to the total.</li>
 *   <li>Tax is computed per line on {@code lineSubtotal - discount} and rounded per line.
 *       Tax-exclusive: {@code tax = round(taxable x rate)}, {@code lineTotal = taxable + tax}.
 *       Tax-inclusive: {@code tax = round(taxable - taxable / (1 + rate))}, {@code lineTotal = taxable}.</li>
 *   <li>Totals are sums of the rounded line figures, never re-derived, so a receipt always adds up.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PricingProperties properties;

    /** A basket line to be priced. {@code unitPrice} comes from the database, never the client. */
    public record Input(UUID productId, String taxClass, BigDecimal unitPrice, BigDecimal quantity) {
    }

    public record Line(
            UUID productId,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal taxRatePercent,
            BigDecimal lineSubtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }

    public record Result(
            List<Line> lines,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            boolean pricesIncludeTax) {
    }

    public boolean pricesIncludeTax() {
        return properties.tax().pricesIncludeTax();
    }

    public Result price(List<Input> inputs, BigDecimal discountPercent) {
        BigDecimal percent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        boolean inclusive = properties.tax().pricesIncludeTax();

        List<BigDecimal> subtotals = inputs.stream()
                .map(in -> money(in.unitPrice().multiply(in.quantity())))
                .toList();
        BigDecimal subtotal = subtotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountTotal = money(subtotal.multiply(percent).divide(HUNDRED, 10, ROUNDING));

        List<Line> lines = new ArrayList<>(inputs.size());
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (int i = 0; i < inputs.size(); i++) {
            Input in = inputs.get(i);
            BigDecimal lineSubtotal = subtotals.get(i);

            BigDecimal discount;
            if (i == inputs.size() - 1) {
                discount = discountTotal.subtract(allocated);
            } else if (subtotal.signum() == 0) {
                discount = BigDecimal.ZERO;
            } else {
                discount = money(discountTotal.multiply(lineSubtotal).divide(subtotal, 10, ROUNDING));
            }
            discount = discount.min(lineSubtotal).max(BigDecimal.ZERO);
            allocated = allocated.add(discount);

            BigDecimal taxable = lineSubtotal.subtract(discount);
            BigDecimal ratePercent = properties.tax().rateFor(in.taxClass());
            BigDecimal rate = ratePercent.divide(HUNDRED, 10, ROUNDING);

            BigDecimal tax;
            BigDecimal lineTotal;
            if (inclusive) {
                BigDecimal net = taxable.divide(BigDecimal.ONE.add(rate), 10, ROUNDING);
                tax = money(taxable.subtract(net));
                lineTotal = taxable;
            } else {
                tax = money(taxable.multiply(rate));
                lineTotal = taxable.add(tax);
            }

            taxTotal = taxTotal.add(tax);
            grandTotal = grandTotal.add(lineTotal);
            lines.add(new Line(in.productId(), in.unitPrice(), in.quantity(), ratePercent,
                    lineSubtotal, discount, tax, lineTotal));
        }

        return new Result(List.copyOf(lines), subtotal, discountTotal, taxTotal, grandTotal, inclusive);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }
}
