package com.pos.sales.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pos.sales.config.PricingProperties;
import com.pos.sales.service.PricingService.Input;
import com.pos.sales.service.PricingService.Result;

class PricingServiceTest {

    private static PricingService service(String standardRate, boolean inclusive) {
        return new PricingService(new PricingProperties(
                new PricingProperties.Tax(BigDecimal.ZERO,
                        Map.of("STANDARD", new BigDecimal(standardRate), "ZERO", BigDecimal.ZERO), inclusive),
                null));
    }

    private static Input line(String taxClass, String price, String qty) {
        return new Input(UUID.randomUUID(), taxClass, new BigDecimal(price), new BigDecimal(qty));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void untaxedBasketTotalsAreTheSumOfLines() {
        Result r = service("0", false).price(List.of(line("STANDARD", "1.99", "3"), line("ZERO", "0.50", "2")), null);

        assertThat(r.subtotal()).isEqualByComparingTo("6.97");
        assertThat(r.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(r.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(r.grandTotal()).isEqualByComparingTo("6.97");
    }

    @Test
    void exclusiveTaxIsAddedOnTopAndRoundedHalfUpPerLine() {
        // 19.99 x 3 = 59.97; 10% discount = 5.997 -> 6.00; taxable 53.97; 5% tax = 2.6985 -> 2.70
        Result r = service("5", false).price(List.of(line("STANDARD", "19.99", "3")), bd("10"));

        assertThat(r.subtotal()).isEqualByComparingTo("59.97");
        assertThat(r.discountTotal()).isEqualByComparingTo("6.00");
        assertThat(r.taxTotal()).isEqualByComparingTo("2.70");
        assertThat(r.grandTotal()).isEqualByComparingTo("56.67");
        assertThat(r.lines().get(0).lineTotal()).isEqualByComparingTo("56.67");
    }

    @Test
    void inclusiveTaxIsExtractedFromThePriceNotAdded() {
        // 105.00 includes 5%: net 100.00, tax 5.00, total stays 105.00
        Result r = service("5", true).price(List.of(line("STANDARD", "105.00", "1")), null);

        assertThat(r.taxTotal()).isEqualByComparingTo("5.00");
        assertThat(r.grandTotal()).isEqualByComparingTo("105.00");
        assertThat(r.pricesIncludeTax()).isTrue();
    }

    @Test
    void zeroRatedAndUnknownClassesPayNoTaxWithADefaultOfZero() {
        Result r = service("18", false).price(List.of(line("ZERO", "10.00", "1"), line("EXOTIC", "10.00", "1")), null);

        assertThat(r.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(r.grandTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void fractionalWeightQuantitiesAreRoundedToTheCent() {
        // 0.333 kg x 1.99 = 0.66267 -> 0.66 ; 1.255 kg x 3.00 = 3.765 -> 3.77 (HALF_UP)
        Result r = service("0", false).price(List.of(line("ZERO", "1.99", "0.333"), line("ZERO", "3.00", "1.255")), null);

        assertThat(r.lines().get(0).lineSubtotal()).isEqualByComparingTo("0.66");
        assertThat(r.lines().get(1).lineSubtotal()).isEqualByComparingTo("3.77");
        assertThat(r.subtotal()).isEqualByComparingTo("4.43");
    }

    @Test
    void halfCentRoundsUp() {
        // 0.05 x 0.5 = 0.025 -> 0.03 ; 0.15 x 0.5 = 0.075 -> 0.08
        Result r = service("0", false).price(List.of(line("ZERO", "0.05", "0.5"), line("ZERO", "0.15", "0.5")), null);

        assertThat(r.lines().get(0).lineSubtotal()).isEqualByComparingTo("0.03");
        assertThat(r.lines().get(1).lineSubtotal()).isEqualByComparingTo("0.08");
    }

    @Test
    void discountIsAllocatedSoLinesAlwaysAddUpToTheTotal() {
        // three equal lines, 10% of 1.00 = 0.10 cannot split evenly into thirds
        Result r = service("0", false).price(List.of(
                line("ZERO", "0.33", "1"), line("ZERO", "0.33", "1"), line("ZERO", "0.34", "1")), bd("10"));

        BigDecimal allocated = r.lines().stream().map(PricingService.Line::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo(r.discountTotal());
        assertThat(r.lines()).allSatisfy(l -> assertThat(l.discountAmount().signum()).isGreaterThanOrEqualTo(0));

        BigDecimal lineSum = r.lines().stream().map(PricingService.Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineSum).isEqualByComparingTo(r.grandTotal());
    }

    @Test
    void aFullDiscountYieldsAZeroNotNegativeTotal() {
        Result r = service("5", false).price(List.of(line("STANDARD", "9.99", "2")), bd("100"));

        assertThat(r.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(r.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(r.lines().get(0).discountAmount()).isEqualByComparingTo("19.98");
    }

    @Test
    void grandTotalEqualsSubtotalMinusDiscountPlusTaxWhenExclusive() {
        Result r = service("12.5", false).price(List.of(
                line("STANDARD", "7.77", "3"), line("STANDARD", "1.11", "7"), line("ZERO", "2.50", "2")), bd("7.5"));

        assertThat(r.grandTotal())
                .isEqualByComparingTo(r.subtotal().subtract(r.discountTotal()).add(r.taxTotal()));
    }
}
