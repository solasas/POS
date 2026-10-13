package com.pos.sales.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.pos.auth.entity.Role;

/** Tax and discount rules, bound from {@code pos.pricing.*}. See README "Pricing and tax". */
@ConfigurationProperties(prefix = "pos.pricing")
public record PricingProperties(Tax tax, Discount discount) {

    public PricingProperties {
        tax = tax == null ? new Tax(null, null, false) : tax;
        discount = discount == null ? new Discount(null, null, null) : discount;
    }

    /**
     * @param defaultRatePercent  rate for tax classes without an explicit entry (0 = untaxed)
     * @param ratesPercent        rate per product tax class, e.g. STANDARD -> 5
     * @param pricesIncludeTax    true when catalog prices already contain tax
     */
    public record Tax(BigDecimal defaultRatePercent, Map<String, BigDecimal> ratesPercent, boolean pricesIncludeTax) {

        public Tax {
            defaultRatePercent = defaultRatePercent == null ? BigDecimal.ZERO : defaultRatePercent;
            Map<String, BigDecimal> normalized = new HashMap<>();
            if (ratesPercent != null) {
                ratesPercent.forEach((taxClass, rate) -> normalized.put(taxClass.toUpperCase(), rate));
            }
            ratesPercent = Map.copyOf(normalized);
            if (defaultRatePercent.signum() < 0 || ratesPercent.values().stream().anyMatch(r -> r.signum() < 0)) {
                throw new IllegalArgumentException("Tax rates must not be negative");
            }
        }

        public BigDecimal rateFor(String taxClass) {
            BigDecimal rate = taxClass == null ? null : ratesPercent.get(taxClass.toUpperCase());
            return rate != null ? rate : defaultRatePercent;
        }
    }

    /** Highest order-level discount percentage each role may apply. */
    public record Discount(BigDecimal cashierMaxPercent, BigDecimal managerMaxPercent, BigDecimal adminMaxPercent) {

        public Discount {
            cashierMaxPercent = cashierMaxPercent == null ? BigDecimal.valueOf(15) : cashierMaxPercent;
            managerMaxPercent = managerMaxPercent == null ? BigDecimal.valueOf(50) : managerMaxPercent;
            adminMaxPercent = adminMaxPercent == null ? BigDecimal.valueOf(100) : adminMaxPercent;
        }

        public BigDecimal maxFor(Role role) {
            return switch (role) {
                case ADMIN -> adminMaxPercent;
                case MANAGER -> managerMaxPercent;
                case CASHIER -> cashierMaxPercent;
            };
        }
    }
}
