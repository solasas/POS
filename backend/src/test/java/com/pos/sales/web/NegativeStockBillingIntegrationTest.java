package com.pos.sales.web;

import static com.pos.support.CheckoutClient.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.auth.entity.Role;
import com.pos.auth.repository.UserRepository;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.support.ApiAuth;
import com.pos.support.CatalogFixtures;
import com.pos.support.CheckoutClient;
import com.pos.support.TestAccounts;

/** The "allow billing out of stock" switch: who may flip it, and what changes when it is on. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NegativeStockBillingIntegrationTest {

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
    private JdbcTemplate jdbc;

    private CheckoutClient client;
    private CatalogFixtures fixtures;
    private String cashierAuth;
    private String managerAuth;
    private TestAccounts.Account manager;

    @BeforeEach
    void setUp() throws Exception {
        client = new CheckoutClient(mockMvc, objectMapper);
        fixtures = new CatalogFixtures(categoryRepository, productRepository);
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        cashierAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.CASHIER));
        manager = accounts.create(Role.MANAGER);
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, manager);
        setSwitch(false);
    }

    @AfterEach
    void switchOff() {
        jdbc.update("update app_settings set value = 'false' where key = 'allow_negative_stock'");
    }

    private void setSwitch(boolean on) throws Exception {
        mockMvc.perform(put("/api/settings").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowNegativeStock\": %s}".formatted(on)))
                .andExpect(status().isOk());
    }

    // ---- the switch itself ---------------------------------------------------------------

    @Test
    void isOffByDefaultAndAnyoneSignedInCanReadIt() throws Exception {
        jdbc.update("update app_settings set value = 'false' where key = 'allow_negative_stock'");

        mockMvc.perform(get("/api/settings").header("Authorization", cashierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowNegativeStock", equalTo(false)));
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyManagersAndAboveCanChangeIt() throws Exception {
        mockMvc.perform(put("/api/settings").header("Authorization", cashierAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowNegativeStock\": true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/settings").header("Authorization", cashierAuth))
                .andExpect(jsonPath("$.allowNegativeStock", equalTo(false)));

        mockMvc.perform(put("/api/settings").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowNegativeStock\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowNegativeStock", equalTo(true)))
                .andExpect(jsonPath("$.updatedBy", equalTo(manager.user().getId().toString())))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void aRequestWithoutAValueIsRejected() throws Exception {
        mockMvc.perform(put("/api/settings").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingItIsAuditedButResavingTheSameValueIsNot() throws Exception {
        long before = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'CHANGE_SETTING' and actor_id = ?",
                Long.class, manager.user().getId());

        setSwitch(true);
        setSwitch(true);
        setSwitch(false);

        long after = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'CHANGE_SETTING' and actor_id = ?",
                Long.class, manager.user().getId());
        assertThat(after - before).isEqualTo(2);
    }

    // ---- switch OFF: previous strict behaviour -------------------------------------------

    @Test
    void whenOffSellingMoreThanTheStockIsStillRejected() throws Exception {
        Product p = fixtures.product("10.00", "1");

        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_STOCK")));
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void whenOffAnOutOfStockProductIsNotAvailableAtThePos() throws Exception {
        Product p = fixtures.product("10.00", "0");

        client.get(cashierAuth, "/api/pos/products/barcode/{b}", p.getBarcode())
                .andExpect(jsonPath("$.available", equalTo(false)));
    }

    // ---- switch ON ---------------------------------------------------------------------------

    @Test
    void whenOnABillCanExceedStockAndTheBalanceGoesNegativeWithALedgerTrail() throws Exception {
        setSwitch(true);
        Product p = fixtures.product("10.00", "1");

        var result = client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 3))
                .andExpect(status().isCreated())
                .andReturn();
        UUID saleId = UUID.fromString(client.json(result).get("id").asText());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("-2");
        var row = jdbc.queryForMap(
                "select quantity_change, stock_before, stock_after from inventory_transactions where sale_id = ?", saleId);
        assertThat((BigDecimal) row.get("quantity_change")).isEqualByComparingTo("-3");
        assertThat((BigDecimal) row.get("stock_before")).isEqualByComparingTo("1");
        assertThat((BigDecimal) row.get("stock_after")).isEqualByComparingTo("-2");
    }

    @Test
    void whenOnAProductWithNoStockCanStillBeSoldAndKeepsGoingNegative() throws Exception {
        setSwitch(true);
        Product p = fixtures.product("4.00", "0");

        client.get(cashierAuth, "/api/pos/products/barcode/{b}", p.getBarcode())
                .andExpect(jsonPath("$.available", equalTo(true)))
                .andExpect(jsonPath("$.stockQuantity", equalTo(0.0)));

        client.checkout(cashierAuth, body("CASH", "10", null, p.getId(), 1)).andExpect(status().isCreated());
        client.checkout(cashierAuth, body("CASH", "10", null, p.getId(), 2)).andExpect(status().isCreated());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("-3");
        mockMvc.perform(get("/api/products/{id}", p.getId()).header("Authorization", cashierAuth))
                .andExpect(jsonPath("$.lowStock", equalTo(true)));
    }

    @Test
    void whenOnOtherRulesStillApply() throws Exception {
        setSwitch(true);
        Product inactive = fixtures.product("4.00", "0", UnitOfMeasure.PIECE, false);
        Product piece = fixtures.product("4.00", "0");

        client.checkout(cashierAuth, body("CARD", null, null, inactive.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("PRODUCT_INACTIVE")));
        client.checkout(cashierAuth, body("CARD", null, null, piece.getId(), "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_QUANTITY")));
        client.checkout(cashierAuth, body("CARD", null, null, UUID.randomUUID(), 1))
                .andExpect(status().isNotFound());
        client.checkout(cashierAuth, body("CASH", "1.00", null, piece.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_PAYMENT")));

        assertThat(fixtures.reload(piece).getStockQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void receivingStockLaterBringsTheBalanceBackUpAndTheLedgerStaysConsistent() throws Exception {
        setSwitch(true);
        Product p = fixtures.product("4.00", "0");
        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 2)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":10,\"reason\":\"Delivery\"}".formatted(p.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockBefore", equalTo(-2.0)))
                .andExpect(jsonPath("$.stockAfter", equalTo(8.0)));

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("8");
        // every ledger row satisfies after = before + change, even through zero
        Long broken = jdbc.queryForObject(
                "select count(*) from inventory_transactions where product_id = ? and stock_after <> stock_before + quantity_change",
                Long.class, p.getId());
        assertThat(broken).isZero();
    }

    @Test
    void turningItBackOffStopsSalesFromGoingFurtherNegative() throws Exception {
        setSwitch(true);
        Product p = fixtures.product("4.00", "0");
        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1)).andExpect(status().isCreated());   // -1

        setSwitch(false);

        client.checkout(cashierAuth, body("CARD", null, null, p.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_STOCK")));
        client.get(cashierAuth, "/api/pos/products/barcode/{b}", p.getBarcode())
                .andExpect(jsonPath("$.available", equalTo(false)));
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("-1");
    }

    @Test
    void manualAdjustmentsBelowZeroFollowTheSwitchToo() throws Exception {
        Product p = fixtures.product("4.00", "2");
        String shrink = "{\"productId\":\"%s\",\"quantityChange\":-5,\"reason\":\"Shrinkage\"}".formatted(p.getId());

        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(shrink))
                .andExpect(status().isConflict());

        setSwitch(true);
        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(shrink))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockAfter", equalTo(-3.0)));
    }

    @Test
    void parallelSalesPastZeroAllSucceedAndTheBalanceIsExact() throws Exception {
        setSwitch(true);
        Product p = fixtures.product("2.00", "2");
        String json = body("CARD", null, null, p.getId(), 1);
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return client.checkout(cashierAuth, json).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            for (Future<Integer> f : futures) {
                assertThat(f.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("-6");
        assertThat(jdbc.queryForObject("select count(*) from inventory_transactions where product_id = ?",
                Long.class, p.getId())).isEqualTo(threads);
    }
}
