package com.pos.sales.web;

import static com.pos.support.CheckoutClient.body;
import static com.pos.support.CheckoutClient.cash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.auth.entity.Role;
import com.pos.auth.repository.UserRepository;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.entity.InventoryTransaction;
import com.pos.inventory.entity.MovementType;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.sales.repository.SaleRepository;
import com.pos.support.ApiAuth;
import com.pos.support.CatalogFixtures;
import com.pos.support.CheckoutClient;
import com.pos.support.TestAccounts;

/** Checkout business rules, pricing, snapshots, ledger, preview and POS lookup against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "pos.pricing.tax.rates-percent.STANDARD=5",
        "pos.store.name=Test Mart",
})
class CheckoutIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private InventoryTransactionRepository ledger;
    @Autowired
    private JdbcTemplate jdbc;

    private CheckoutClient client;
    private CatalogFixtures fixtures;
    private String cashierAuth;
    private String managerAuth;
    private TestAccounts.Account cashier;

    @BeforeEach
    void setUp() throws Exception {
        client = new CheckoutClient(mockMvc, objectMapper);
        fixtures = new CatalogFixtures(categoryRepository, productRepository);
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        cashier = accounts.create(Role.CASHIER);
        cashierAuth = ApiAuth.bearer(mockMvc, objectMapper, cashier);
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.MANAGER));
    }

    // ---- successful sales ---------------------------------------------------------------

    @Test
    void cashSaleIsCompletedWithServerCalculatedTotalsAndChange() throws Exception {
        Product p = fixtures.product("19.99", "10");

        // 3 x 19.99 = 59.97, 5% tax = 3.00 (2.9985), total 62.97
        MvcResult result = client.checkout(cashierAuth, cash("70.00", p.getId(), 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.subtotal", equalTo(59.97)))
                .andExpect(jsonPath("$.taxTotal", equalTo(3.0)))
                .andExpect(jsonPath("$.grandTotal", equalTo(62.97)))
                .andExpect(jsonPath("$.payment.method", equalTo("CASH")))
                .andExpect(jsonPath("$.payment.status", equalTo("RECORDED")))
                .andExpect(jsonPath("$.payment.amountTendered", equalTo(70.0)))
                .andExpect(jsonPath("$.payment.changeDue", equalTo(7.03)))
                .andExpect(jsonPath("$.saleNumber").value(org.hamcrest.Matchers.startsWith("S")))
                .andReturn();

        assertThat(client.json(result).get("cashierId").asText()).isEqualTo(cashier.user().getId().toString());
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("7");
    }

    @Test
    void upiSaleIsRecordedWithoutTenderedOrChange() throws Exception {
        Product p = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, body("UPI", null, null, p.getId(), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.method", equalTo("UPI")))
                .andExpect(jsonPath("$.payment.status", equalTo("RECORDED")))
                .andExpect(jsonPath("$.payment.amount", equalTo(21.0)))
                .andExpect(jsonPath("$.payment.amountTendered", nullValue()))
                .andExpect(jsonPath("$.payment.changeDue", nullValue()));
    }

    @Test
    void cardSaleIsRecordedAndDeductsStock() throws Exception {
        Product p = fixtures.product("2.00", "5");

        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.method", equalTo("CARD")));

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void clientSuppliedTotalsAreIgnored() throws Exception {
        Product p = fixtures.product("100.00", "5");
        String json = """
                {"items":[{"productId":"%s","quantity":1,"unitPrice":0.01,"lineTotal":0.01}],
                 "grandTotal":0.01,"subtotal":0.01,"status":"VOIDED",
                 "payment":{"method":"CASH","amountTendered":105.00,"amount":0.01}}
                """.formatted(p.getId());

        client.checkout(cashierAuth, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grandTotal", equalTo(105.0)))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.items[0].unitPrice", equalTo(100.0)));
    }

    @Test
    void weightProductsAcceptDecimalQuantitiesAndRoundHalfUp() throws Exception {
        Product kg = fixtures.product("3.00", "10", UnitOfMeasure.KG, true);

        // 1.255 kg x 3.00 = 3.765 -> 3.77 ; zero-rate not configured for STANDARD -> 5% tax 0.19 (0.1885)
        client.checkout(cashierAuth, body("CARD", null, null, kg.getId(), "1.255"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal", equalTo(3.77)))
                .andExpect(jsonPath("$.taxTotal", equalTo(0.19)))
                .andExpect(jsonPath("$.grandTotal", equalTo(3.96)))
                .andExpect(jsonPath("$.items[0].quantity", equalTo(1.255)));

        assertThat(fixtures.reload(kg).getStockQuantity()).isEqualByComparingTo("8.745");
    }

    // ---- request validation -------------------------------------------------------------

    @Test
    void insufficientCashIsRejectedWithoutSideEffects() throws Exception {
        Product p = fixtures.product("10.00", "5");
        long salesBefore = saleRepository.count();

        client.checkout(cashierAuth, cash("10.49", p.getId(), 1))   // payable is 10.50
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_PAYMENT")))
                .andExpect(jsonPath("$.fieldErrors['payment.amountTendered']").exists());

        assertThat(saleRepository.count()).isEqualTo(salesBefore);
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void cashWithoutAnAmountTenderedIsRejected() throws Exception {
        Product p = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, body("CASH", null, null, p.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_PAYMENT")));
    }

    @Test
    void tenderedAmountOnANonCashPaymentIsRejected() throws Exception {
        Product p = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, body("CARD", "20.00", null, p.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_PAYMENT")));
    }

    @Test
    void zeroNegativeAndFractionalQuantitiesAreRejected() throws Exception {
        Product piece = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, cash("100", piece.getId(), 0)).andExpect(status().isBadRequest());
        client.checkout(cashierAuth, cash("100", piece.getId(), -1)).andExpect(status().isBadRequest());
        client.checkout(cashierAuth, cash("100", piece.getId(), "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_QUANTITY")));
        client.checkout(cashierAuth, cash("100", piece.getId(), "0.0004")).andExpect(status().isBadRequest());

        assertThat(fixtures.reload(piece).getStockQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void anEmptyBasketIsRejected() throws Exception {
        client.checkout(cashierAuth, "{\"items\":[],\"payment\":{\"method\":\"CARD\"}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateProductLinesAreRejected() throws Exception {
        Product p = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1, p.getId(), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_LINE")));
    }

    @Test
    void unknownProductsAreRejected() throws Exception {
        client.checkout(cashierAuth, body("CARD", null, null, UUID.randomUUID(), 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PRODUCT_NOT_FOUND")));
    }

    @Test
    void inactiveProductsCannotBeSold() throws Exception {
        Product inactive = fixtures.product("10.00", "5", UnitOfMeasure.PIECE, false);

        client.checkout(cashierAuth, body("CARD", null, null, inactive.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("PRODUCT_INACTIVE")));
        assertThat(fixtures.reload(inactive).getStockQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void insufficientStockIsRejectedAndNothingChanges() throws Exception {
        Product scarce = fixtures.product("10.00", "2");
        Product plenty = fixtures.product("10.00", "50");

        client.checkout(cashierAuth, body("CARD", null, null, scarce.getId(), 3, plenty.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_STOCK")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("available 2")));

        assertThat(fixtures.reload(scarce).getStockQuantity()).isEqualByComparingTo("2");
        assertThat(fixtures.reload(plenty).getStockQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void discountRulesFollowTheCallersRole() throws Exception {
        Product p = fixtures.product("100.00", "20");

        client.checkout(cashierAuth, body("CARD", null, "100.01", p.getId(), 1)).andExpect(status().isBadRequest());
        client.checkout(cashierAuth, body("CARD", null, "-1", p.getId(), 1)).andExpect(status().isBadRequest());
        client.checkout(cashierAuth, body("CARD", null, "15.01", p.getId(), 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("DISCOUNT_NOT_PERMITTED")));
        client.checkout(cashierAuth, body("CARD", null, "15", p.getId(), 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountTotal", equalTo(15.0)))
                .andExpect(jsonPath("$.grandTotal", equalTo(89.25)));   // (100 - 15) * 1.05
        client.checkout(managerAuth, body("CARD", null, "40", p.getId(), 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountTotal", equalTo(40.0)));
        client.checkout(managerAuth, body("CARD", null, "50.01", p.getId(), 1)).andExpect(status().isForbidden());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("18");   // only 2 sales succeeded
    }

    @Test
    void checkoutRequiresAnIdempotencyKey() throws Exception {
        Product p = fixtures.product("10.00", "5");

        client.checkout(cashierAuth, null, body("CARD", null, null, p.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_IDEMPOTENCY_KEY")));
    }

    // ---- integrity: snapshots, ledger, immutability -------------------------------------

    @Test
    void stockDecreasesExactlyOnceAndLedgerMatchesTheSale() throws Exception {
        Product a = fixtures.product("4.00", "10");
        Product b = fixtures.product("2.50", "6", UnitOfMeasure.KG, true);

        MvcResult result = client.checkout(cashierAuth, body("CARD", null, null, a.getId(), 4, b.getId(), "1.5"))
                .andExpect(status().isCreated()).andReturn();
        UUID saleId = UUID.fromString(client.json(result).get("id").asText());

        assertThat(fixtures.reload(a).getStockQuantity()).isEqualByComparingTo("6");
        assertThat(fixtures.reload(b).getStockQuantity()).isEqualByComparingTo("4.5");

        List<InventoryTransaction> entries = ledger.findAllBySaleIdOrderByCreatedAtAsc(saleId);
        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getMovementType()).isEqualTo(MovementType.SALE);
            assertThat(e.getQuantityChange().signum()).isNegative();
            assertThat(e.getStockAfter()).isEqualByComparingTo(e.getStockBefore().add(e.getQuantityChange()));
            assertThat(e.getActorId()).isEqualTo(cashier.user().getId());
        });
        InventoryTransaction forA = entries.stream()
                .filter(e -> e.getProduct().getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(forA.getQuantityChange()).isEqualByComparingTo("-4");
        assertThat(forA.getStockBefore()).isEqualByComparingTo("10");
        assertThat(forA.getStockAfter()).isEqualByComparingTo("6");
    }

    @Test
    void historicalSalesKeepTheOriginalNameAndPriceAfterTheProductChanges() throws Exception {
        Product p = fixtures.product("9.99", "5");
        String originalName = p.getName();
        MvcResult result = client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1))
                .andExpect(status().isCreated()).andReturn();
        String saleId = client.json(result).get("id").asText();
        String saleNumber = client.json(result).get("saleNumber").asText();

        Product edited = fixtures.reload(p);
        edited.setName("Renamed Later");
        edited.setPrice(new BigDecimal("99.99"));
        edited.setSku("CHANGED-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        edited.setActive(false);
        productRepository.saveAndFlush(edited);

        client.get(cashierAuth, "/api/sales/{id}", saleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name", equalTo(originalName)))
                .andExpect(jsonPath("$.items[0].unitPrice", equalTo(9.99)))
                .andExpect(jsonPath("$.items[0].sku", equalTo(p.getSku())));
        client.get(cashierAuth, "/api/sales/receipt/{n}", saleNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.items[0].name", equalTo(originalName)))
                .andExpect(jsonPath("$.store.name", equalTo("Test Mart")));
    }

    @Test
    void saleItemsAndLedgerEntriesCannotBeModifiedOrDeleted() throws Exception {
        Product p = fixtures.product("5.00", "5");
        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1)).andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("update sale_items set unit_price = 0.01 where product_id = ?", p.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from sale_items where product_id = ?", p.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "update inventory_transactions set quantity_change = -99 where product_id = ?", p.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from inventory_transactions where product_id = ?", p.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from sales"))
                .hasMessageContaining("append-only");
    }

    // ---- profit --------------------------------------------------------------------------

    private Product productWithCost(String price, String cost, String stock) {
        return productWithCost(price, cost, stock, UnitOfMeasure.PIECE);
    }

    private Product productWithCost(String price, String cost, String stock, UnitOfMeasure unit) {
        Product p = fixtures.product(price, stock, unit, true);
        p.setCostPrice(new BigDecimal(cost));
        return productRepository.saveAndFlush(p);
    }

    @Test
    void profitIsNetRevenueLessTheCostSnapshottedAtSaleTime() throws Exception {
        Product p = productWithCost("100.00", "60.00", "10");

        // 2 x 100 = 200, 10% off = 20, taxable 180, 5% tax = 9 -> pays 189.
        // Net revenue is 180 (tax is not income); cost is 2 x 60 = 120; profit 60.
        MvcResult result = client.checkout(managerAuth, body("CARD", null, "10", p.getId(), 2))
                .andExpect(status().isCreated())
                .andReturn();
        String saleId = client.json(result).get("id").asText();

        client.get(managerAuth, "/api/sales/{id}", saleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal", equalTo(189.0)))
                .andExpect(jsonPath("$.costTotal", equalTo(120.0)))
                .andExpect(jsonPath("$.profit", equalTo(60.0)));

        Product edited = fixtures.reload(p);
        edited.setCostPrice(new BigDecimal("999.00"));
        productRepository.saveAndFlush(edited);

        client.get(managerAuth, "/api/sales/{id}", saleId)
                .andExpect(jsonPath("$.costTotal", equalTo(120.0)))
                .andExpect(jsonPath("$.profit", equalTo(60.0)));
    }

    @Test
    void summaryProfitMatchesTheSumOfTheSalesInThePeriod() throws Exception {
        Instant from = Instant.now();
        Product a = productWithCost("100.00", "60.00", "10");
        Product b = productWithCost("20.00", "5.50", "10", UnitOfMeasure.KG);

        // a: 2 x 100 = 200 (+10 tax), cost 120 -> profit 80
        client.checkout(cashierAuth, body("CARD", null, null, a.getId(), 2)).andExpect(status().isCreated());
        // b: 1.5 kg x 20 = 30 (+1.50 tax), cost 1.5 x 5.50 = 8.25 -> profit 21.75
        client.checkout(cashierAuth, body("UPI", null, null, b.getId(), "1.5")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/sales/summary").header("Authorization", managerAuth)
                        .param("from", from.toString())
                        .param("to", Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleCount", equalTo(2)))
                .andExpect(jsonPath("$.grandTotal", equalTo(241.5)))      // 210 + 31.50
                .andExpect(jsonPath("$.costTotal", equalTo(128.25)))      // 120 + 8.25
                .andExpect(jsonPath("$.profit", equalTo(101.75)));        // 80 + 21.75
    }

    @Test
    void cashiersNeverSeeCostOrProfit() throws Exception {
        Product p = productWithCost("100.00", "60.00", "10");

        MvcResult result = client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costTotal", nullValue()))
                .andExpect(jsonPath("$.profit", nullValue()))
                .andReturn();
        String saleId = client.json(result).get("id").asText();

        client.get(cashierAuth, "/api/sales/{id}", saleId)
                .andExpect(jsonPath("$.costTotal", nullValue()))
                .andExpect(jsonPath("$.profit", nullValue()));
        client.get(cashierAuth, "/api/sales/receipt/{n}", client.json(result).get("saleNumber").asText())
                .andExpect(jsonPath("$.sale.profit", nullValue()));
    }

    @Test
    void salesBeyondStockStillCarryTheirCostSoProfitIsComplete() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/settings")
                .header("Authorization", managerAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowNegativeStock\": true}")).andExpect(status().isOk());
        try {
            Product p = productWithCost("50.00", "30.00", "0");

            MvcResult result = client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1))
                    .andExpect(status().isCreated()).andReturn();

            // 50 net of 5% tax = 50 revenue (tax added on top), cost 30 -> profit 20
            client.get(managerAuth, "/api/sales/{id}", client.json(result).get("id").asText())
                    .andExpect(jsonPath("$.profit", equalTo(20.0)));
        } finally {
            jdbc.update("update app_settings set value = 'false' where key = 'allow_negative_stock'");
        }
    }

    // ---- preview -------------------------------------------------------------------------

    @Test
    void previewMatchesCheckoutTotalsAndChangesNothing() throws Exception {
        Product p = fixtures.product("19.99", "10");
        String preview = "{\"items\":[{\"productId\":\"%s\",\"quantity\":3}],\"discountPercent\":10}".formatted(p.getId());

        MvcResult previewResult = mockMvc.perform(post("/api/sales/preview")
                        .header("Authorization", cashierAuth).contentType(MediaType.APPLICATION_JSON).content(preview))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal", equalTo(59.97)))
                .andExpect(jsonPath("$.discountTotal", equalTo(6.0)))
                .andExpect(jsonPath("$.taxTotal", equalTo(2.7)))
                .andExpect(jsonPath("$.grandTotal", equalTo(56.67)))
                .andExpect(jsonPath("$.maxDiscountPercent", equalTo(15)))
                .andReturn();
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("10");

        JsonNode quoted = client.json(previewResult);
        client.checkout(cashierAuth, body("CARD", null, "10", p.getId(), 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grandTotal", equalTo(quoted.get("grandTotal").asDouble())));
    }

    // ---- POS lookup ----------------------------------------------------------------------

    @Test
    void barcodeLookupFindsByExactBarcodeOrSkuAndFlagsInactiveProducts() throws Exception {
        Product active = fixtures.product("3.00", "4");
        Product inactive = fixtures.product("3.00", "4", UnitOfMeasure.PIECE, false);

        client.get(cashierAuth, "/api/pos/products/barcode/{b}", active.getBarcode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(active.getId().toString())))
                .andExpect(jsonPath("$.available", equalTo(true)))
                .andExpect(jsonPath("$.wholeQuantitiesOnly", equalTo(true)));
        client.get(cashierAuth, "/api/pos/products/barcode/{b}", active.getSku().toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(active.getId().toString())));
        client.get(cashierAuth, "/api/pos/products/barcode/{b}", inactive.getBarcode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)))
                .andExpect(jsonPath("$.available", equalTo(false)));
        client.get(cashierAuth, "/api/pos/products/barcode/{b}", "0000000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("UNKNOWN_BARCODE")));
    }

    @Test
    void textSearchReturnsOnlyActiveMatches() throws Exception {
        Product active = fixtures.product("3.00", "4");
        fixtures.product("3.00", "4", UnitOfMeasure.PIECE, false);
        String term = active.getSku().substring(3);   // unique suffix shared by that fixture's name

        mockMvc.perform(get("/api/pos/products").param("query", term).header("Authorization", cashierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo(active.getId().toString())));
        mockMvc.perform(get("/api/pos/products").param("query", "").header("Authorization", cashierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- manual stock adjustments (prerequisite for selling) -----------------------------

    @Test
    void managerAdjustmentsAppendToTheLedgerAndNeverGoNegative() throws Exception {
        Product p = fixtures.product("5.00", "0");

        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":12,\"reason\":\"Delivery\"}".formatted(p.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType", equalTo("ADJUSTMENT")))
                .andExpect(jsonPath("$.stockBefore", equalTo(0.0)))
                .andExpect(jsonPath("$.stockAfter", equalTo(12.0)));
        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":-13,\"reason\":\"Damage\"}".formatted(p.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_STOCK")));
        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":1.5,\"reason\":\"Half\"}".formatted(p.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":1,\"reason\":\" \"}".formatted(p.getId())))
                .andExpect(status().isBadRequest());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("12");
        assertThat(ledger.findAll().stream().filter(t -> t.getProduct().getId().equals(p.getId()))).hasSize(1);
    }

    @Test
    void cashiersCannotAdjustStockOrReadTheLedger() throws Exception {
        Product p = fixtures.product("5.00", "3");

        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", cashierAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":100,\"reason\":\"Nice try\"}".formatted(p.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inventory/transactions").header("Authorization", cashierAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inventory/transactions").param("productId", p.getId().toString())
                        .header("Authorization", managerAuth))
                .andExpect(status().isOk());
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("3");
    }
}
