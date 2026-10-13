package com.pos.sales.web;

import static com.pos.support.CheckoutClient.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.support.ApiAuth;
import com.pos.support.CatalogFixtures;
import com.pos.support.CheckoutClient;
import com.pos.support.TestAccounts;

/**
 * Proves checkout is all-or-nothing. Faults are injected inside PostgreSQL itself with temporary
 * triggers, so a real INSERT fails part-way through a real transaction (no mocks involved).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutAtomicityIntegrationTest {

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
    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        client = new CheckoutClient(mockMvc, objectMapper);
        fixtures = new CatalogFixtures(categoryRepository, productRepository);
        auth = ApiAuth.bearer(mockMvc, objectMapper,
                new TestAccounts(userRepository, passwordEncoder).create(Role.CASHIER));
        jdbc.execute("""
                create or replace function test_fail_insert() returns trigger as $$
                begin raise exception 'injected failure on %', tg_table_name; end;
                $$ language plpgsql
                """);
    }

    @AfterEach
    void removeFaults() {
        jdbc.execute("drop trigger if exists test_fail_payment on payments");
        jdbc.execute("drop trigger if exists test_fail_ledger on inventory_transactions");
    }

    private long salesWithKey(String key) {
        return jdbc.queryForObject("select count(*) from sales where idempotency_key = ?", Long.class, key);
    }

    @Test
    void aFailureWhileCreatingTheSaleRollsBackItemsStockAndLedger() throws Exception {
        Product a = fixtures.product("5.00", "10");
        Product b = fixtures.product("7.00", "10");
        // the payment row is inserted after the sale and its items, so those must be undone too
        jdbc.execute("create trigger test_fail_payment before insert on payments "
                + "for each row execute function test_fail_insert()");
        String key = CheckoutClient.newKey();

        client.checkout(auth, key, body("CARD", null, null, a.getId(), 2, b.getId(), 3))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", equalTo("An unexpected error occurred. Please try again.")));

        assertThat(salesWithKey(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sale_items where product_id in (?, ?)",
                Long.class, a.getId(), b.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inventory_transactions where product_id in (?, ?)",
                Long.class, a.getId(), b.getId())).isZero();
        assertThat(fixtures.reload(a).getStockQuantity()).isEqualByComparingTo("10");
        assertThat(fixtures.reload(b).getStockQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void aFailureWhileUpdatingInventoryDoesNotLeaveACompletedSale() throws Exception {
        Product ok = fixtures.product("5.00", "10");
        Product poisoned = fixtures.product("7.00", "10");
        jdbc.execute("create trigger test_fail_ledger before insert on inventory_transactions "
                + "for each row when (new.product_id = '" + poisoned.getId() + "') "
                + "execute function test_fail_insert()");
        String key = CheckoutClient.newKey();

        client.checkout(auth, key, body("CARD", null, null, ok.getId(), 2, poisoned.getId(), 1))
                .andExpect(status().isInternalServerError());

        assertThat(salesWithKey(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payments p join sales s on s.id = p.sale_id "
                + "where s.idempotency_key = ?", Long.class, key)).isZero();
        assertThat(fixtures.reload(ok).getStockQuantity()).isEqualByComparingTo("10");
        assertThat(fixtures.reload(poisoned).getStockQuantity()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select count(*) from inventory_transactions where product_id = ?",
                Long.class, ok.getId())).isZero();
    }

    @Test
    void aFailedCheckoutDoesNotConsumeItsIdempotencyKey() throws Exception {
        Product p = fixtures.product("5.00", "10");
        jdbc.execute("create trigger test_fail_payment before insert on payments "
                + "for each row execute function test_fail_insert()");
        String key = CheckoutClient.newKey();
        String json = body("CARD", null, null, p.getId(), 1);

        client.checkout(auth, key, json).andExpect(status().isInternalServerError());

        removeFaults();
        client.checkout(auth, key, json)
                .andExpect(status().isCreated());   // same key, now succeeds as a fresh attempt

        assertThat(salesWithKey(key)).isEqualTo(1);
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("9");
    }
}
