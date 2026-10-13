package com.pos.sales.web;

import static com.pos.support.CheckoutClient.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.MvcResult;
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

/** Overselling protection, lock ordering and idempotency under genuine parallel requests. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutConcurrencyIntegrationTest {

    private static final int THREADS = 8;

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
    private TestAccounts accounts;
    private String auth;
    private String managerAuth;

    @BeforeEach
    void setUp() throws Exception {
        client = new CheckoutClient(mockMvc, objectMapper);
        fixtures = new CatalogFixtures(categoryRepository, productRepository);
        accounts = new TestAccounts(userRepository, passwordEncoder);
        auth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.CASHIER));
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.MANAGER));
    }

    private record Response(int status, String body, String replayHeader) {
    }

    /** Runs all callables at the same instant and returns their results. */
    private List<Response> race(List<Callable<Response>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Response> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Response> results = new ArrayList<>();
            for (Future<Response> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Response send(String authorization, String key, String json) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/sales/checkout")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        return new Response(r.getResponse().getStatus(), r.getResponse().getContentAsString(),
                r.getResponse().getHeader("Idempotent-Replay"));
    }

    private long count(List<Response> results, int status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    // ---- overselling ---------------------------------------------------------------------

    @Test
    void twoCashiersCannotBothSellTheLastUnit() throws Exception {
        Product last = fixtures.product("10.00", "1");
        String json = body("CARD", null, null, last.getId(), 1);

        List<Response> results = race(List.of(
                () -> send(auth, CheckoutClient.newKey(), json),
                () -> send(managerAuth, CheckoutClient.newKey(), json)));

        assertThat(count(results, 201)).isEqualTo(1);
        assertThat(count(results, 409)).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 409).findFirst().orElseThrow().body())
                .contains("INSUFFICIENT_STOCK");
        assertThat(fixtures.reload(last).getStockQuantity()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from sale_items where product_id = ?",
                Long.class, last.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_transactions where product_id = ?",
                Long.class, last.getId())).isEqualTo(1);
    }

    @Test
    void manyParallelSalesNeverSellMoreThanTheStockOnHand() throws Exception {
        Product scarce = fixtures.product("2.00", "5");
        String json = body("CARD", null, null, scarce.getId(), 1);

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> send(auth, CheckoutClient.newKey(), json));
        }
        List<Response> results = race(tasks);

        assertThat(count(results, 201)).isEqualTo(5);
        assertThat(count(results, 409)).isEqualTo(THREADS - 5);
        assertThat(fixtures.reload(scarce).getStockQuantity()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from sale_items where product_id = ?",
                java.math.BigDecimal.class, scarce.getId())).isEqualByComparingTo("5");
    }

    @Test
    void overlappingBasketsInOppositeOrderDoNotDeadlock() throws Exception {
        Product p1 = fixtures.product("1.00", "1000");
        Product p2 = fixtures.product("1.00", "1000");
        String forward = body("CARD", null, null, p1.getId(), 1, p2.getId(), 1);
        String reverse = body("CARD", null, null, p2.getId(), 1, p1.getId(), 1);

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String json = i % 2 == 0 ? forward : reverse;
            tasks.add(() -> send(auth, CheckoutClient.newKey(), json));
        }
        List<Response> results = race(tasks);

        assertThat(count(results, 201)).isEqualTo(THREADS);
        assertThat(fixtures.reload(p1).getStockQuantity()).isEqualByComparingTo(String.valueOf(1000 - THREADS));
        assertThat(fixtures.reload(p2).getStockQuantity()).isEqualByComparingTo(String.valueOf(1000 - THREADS));
    }

    // ---- idempotency ---------------------------------------------------------------------

    @Test
    void retryingWithTheSameKeyReturnsTheOriginalSaleAndDeductsStockOnce() throws Exception {
        Product p = fixtures.product("10.00", "5");
        String key = CheckoutClient.newKey();
        String json = body("CARD", null, null, p.getId(), 2);

        MvcResult first = client.checkout(auth, key, json).andExpect(status().isCreated()).andReturn();
        String saleNumber = client.json(first).get("saleNumber").asText();

        client.checkout(auth, key, json)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.saleNumber", equalTo(saleNumber)));

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("select count(*) from sales where idempotency_key = ?",
                Long.class, key)).isEqualTo(1);
    }

    @Test
    void aReorderedButIdenticalRequestCountsAsTheSameRequest() throws Exception {
        Product a = fixtures.product("1.00", "5");
        Product b = fixtures.product("1.00", "5");
        String key = CheckoutClient.newKey();

        client.checkout(auth, key, body("CARD", null, null, a.getId(), 1, b.getId(), 2)).andExpect(status().isCreated());
        client.checkout(auth, key, body("CARD", null, null, b.getId(), "2.0", a.getId(), 1))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    void reusingAKeyWithADifferentRequestIsRejected() throws Exception {
        Product p = fixtures.product("10.00", "5");
        String key = CheckoutClient.newKey();

        client.checkout(auth, key, body("CARD", null, null, p.getId(), 1)).andExpect(status().isCreated());
        client.checkout(auth, key, body("CARD", null, null, p.getId(), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_KEY_REUSED")));
        client.checkout(auth, key, body("UPI", null, null, p.getId(), 1))
                .andExpect(status().isConflict());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void anotherUserCannotReplayOrHijackSomeoneElsesKey() throws Exception {
        Product p = fixtures.product("10.00", "5");
        String key = CheckoutClient.newKey();
        String json = body("CARD", null, null, p.getId(), 1);

        client.checkout(auth, key, json).andExpect(status().isCreated());
        client.checkout(managerAuth, key, json)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_KEY_REUSED")));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneSale() throws Exception {
        Product p = fixtures.product("10.00", "10");
        String key = CheckoutClient.newKey();
        String json = body("CARD", null, null, p.getId(), 1);

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> send(auth, key, json));
        }
        List<Response> results = race(tasks);

        assertThat(count(results, 201)).isEqualTo(1);
        assertThat(count(results, 200)).isEqualTo(THREADS - 1);
        assertThat(results).allSatisfy(r -> assertThat(r.status()).isIn(200, 201));

        Set<String> saleNumbers = new HashSet<>();
        for (Response r : results) {
            saleNumbers.add(objectMapper.readTree(r.body()).get("saleNumber").asText());
        }
        assertThat(saleNumbers).hasSize(1);
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select count(*) from sales where idempotency_key = ?",
                Long.class, key)).isEqualTo(1);
    }

    @Test
    void aRejectedCheckoutDoesNotConsumeTheKey() throws Exception {
        Product p = fixtures.product("10.00", "0");
        String key = CheckoutClient.newKey();
        String json = body("CARD", null, null, p.getId(), 1);

        client.checkout(auth, key, json)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_STOCK")));

        // stock arrives, the cashier retries the very same attempt
        mockMvc.perform(post("/api/inventory/adjustments").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantityChange\":3,\"reason\":\"Delivery\"}".formatted(p.getId())))
                .andExpect(status().isCreated());
        client.checkout(auth, key, json).andExpect(status().isCreated());

        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("2");
    }
}
