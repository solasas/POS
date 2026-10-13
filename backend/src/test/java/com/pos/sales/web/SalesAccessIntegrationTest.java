package com.pos.sales.web;

import static com.pos.support.CheckoutClient.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

/** Who may see which sales, and the sales-history filters. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SalesAccessIntegrationTest {

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

    private CheckoutClient client;
    private CatalogFixtures fixtures;
    private TestAccounts.Account cashierA;
    private TestAccounts.Account cashierB;
    private String authA;
    private String authB;
    private String managerAuth;
    private String adminAuth;
    private String saleAId;
    private String saleANumber;
    private String saleBId;
    private String saleBNumber;

    @BeforeEach
    void setUp() throws Exception {
        client = new CheckoutClient(mockMvc, objectMapper);
        fixtures = new CatalogFixtures(categoryRepository, productRepository);
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        cashierA = accounts.create(Role.CASHIER);
        cashierB = accounts.create(Role.CASHIER);
        authA = ApiAuth.bearer(mockMvc, objectMapper, cashierA);
        authB = ApiAuth.bearer(mockMvc, objectMapper, cashierB);
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.MANAGER));
        adminAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.ADMIN));

        Product p = fixtures.product("10.00", "100");
        MvcResult a = client.checkout(authA, body("CARD", null, null, p.getId(), 1))
                .andExpect(status().isCreated()).andReturn();
        saleAId = client.json(a).get("id").asText();
        saleANumber = client.json(a).get("saleNumber").asText();
        MvcResult b = client.checkout(authB, body("UPI", null, null, p.getId(), 2))
                .andExpect(status().isCreated()).andReturn();
        saleBId = client.json(b).get("id").asText();
        saleBNumber = client.json(b).get("saleNumber").asText();
    }

    // ---- authentication ------------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        Product p = fixtures.product("1.00", "5");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sales/checkout")
                        .header("Idempotency-Key", CheckoutClient.newKey())
                        .contentType("application/json")
                        .content(body("CARD", null, null, p.getId(), 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sales")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sales/my-sales")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pos/products").param("query", "x")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sales/{id}", saleAId)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedUsersTokenCanNoLongerCheckOut() throws Exception {
        TestAccounts.Account fired = new TestAccounts(userRepository, passwordEncoder).create(Role.CASHIER);
        String firedAuth = ApiAuth.bearer(mockMvc, objectMapper, fired);
        Product p = fixtures.product("1.00", "5");

        var user = userRepository.findById(fired.user().getId()).orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        client.checkout(firedAuth, body("CARD", null, null, p.getId(), 1)).andExpect(status().isUnauthorized());
        assertThat(fixtures.reload(p).getStockQuantity()).isEqualByComparingTo("5");
    }

    // ---- cashier scope -------------------------------------------------------------------

    @Test
    void cashiersCannotUseTheAllSalesListing() throws Exception {
        mockMvc.perform(get("/api/sales").header("Authorization", authA)).andExpect(status().isForbidden());
    }

    @Test
    void cashiersSeeOnlyTheirOwnSalesInMySales() throws Exception {
        mockMvc.perform(get("/api/sales/my-sales").header("Authorization", authA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].cashierId", everyItem(equalTo(cashierA.user().getId().toString()))))
                .andExpect(jsonPath("$.content[?(@.saleNumber=='%s')]".formatted(saleANumber)).exists())
                .andExpect(jsonPath("$.content[?(@.saleNumber=='%s')]".formatted(saleBNumber)).isEmpty());
    }

    @Test
    void cashiersCannotReadAnotherCashiersSaleOrReceipt() throws Exception {
        client.get(authA, "/api/sales/{id}", saleBId)
                .andExpect(status().isNotFound());
        client.get(authA, "/api/sales/receipt/{n}", saleBNumber)
                .andExpect(status().isNotFound());
        client.get(authA, "/api/sales/{id}", saleAId).andExpect(status().isOk());
        client.get(authA, "/api/sales/receipt/{n}", saleANumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sale.saleNumber", equalTo(saleANumber)));
    }

    // ---- manager / admin scope and filters -------------------------------------------------

    @Test
    void managersAndAdminsSeeEveryonesSales() throws Exception {
        for (String auth : new String[] {managerAuth, adminAuth}) {
            mockMvc.perform(get("/api/sales").header("Authorization", auth).param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.saleNumber=='%s')]".formatted(saleANumber)).exists())
                    .andExpect(jsonPath("$.content[?(@.saleNumber=='%s')]".formatted(saleBNumber)).exists());
            client.get(auth, "/api/sales/{id}", saleBId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashierName").exists());
            client.get(auth, "/api/sales/receipt/{n}", saleANumber).andExpect(status().isOk());
        }
    }

    @Test
    void historyCanBeFilteredBySaleNumberPaymentMethodCashierStatusAndDate() throws Exception {
        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("saleNumber", saleANumber.toLowerCase()))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].saleNumber", equalTo(saleANumber)))
                .andExpect(jsonPath("$.content[0].itemCount", equalTo(1)));

        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("cashierId", cashierB.user().getId().toString()))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].paymentMethod", equalTo("UPI")));

        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("paymentMethod", "UPI").param("size", "100"))
                .andExpect(jsonPath("$.content[*].paymentMethod", everyItem(equalTo("UPI"))));

        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("status", "VOIDED").param("saleNumber", saleANumber))
                .andExpect(jsonPath("$.totalElements", equalTo(0)));

        Instant now = Instant.now();
        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("saleNumber", saleANumber)
                        .param("from", now.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth)
                        .param("saleNumber", saleANumber)
                        .param("from", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        mockMvc.perform(get("/api/sales").header("Authorization", managerAuth).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(1)))
                .andExpect(jsonPath("$.content.length()", equalTo(1)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].saleNumber", equalTo(saleBNumber)));   // B was created last
    }

    @Test
    void summaryTotalsCompletedSalesByPaymentMethodWithinTheCallersScope() throws Exception {
        Instant now = Instant.now();
        String from = now.minus(1, ChronoUnit.HOURS).toString();
        String to = now.plus(1, ChronoUnit.HOURS).toString();

        // cashier A rang up one 10.00 card sale; B one 20.00 UPI sale (two units at 10.00)
        mockMvc.perform(get("/api/sales/summary").header("Authorization", authA)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleCount", equalTo(1)))
                .andExpect(jsonPath("$.grandTotal", equalTo(10.0)))
                .andExpect(jsonPath("$.byPaymentMethod[0].method", equalTo("CARD")));

        mockMvc.perform(get("/api/sales/summary").header("Authorization", managerAuth)
                        .param("from", from).param("to", to))
                .andExpect(jsonPath("$.saleCount", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.byPaymentMethod[?(@.method=='UPI')]").exists());

        mockMvc.perform(get("/api/sales/summary").header("Authorization", managerAuth)
                        .param("from", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.saleCount", equalTo(0)))
                .andExpect(jsonPath("$.grandTotal", equalTo(0)));

        mockMvc.perform(get("/api/sales/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void summaryProfitIsOnlyVisibleToManagersAndAdmins() throws Exception {
        Instant now = Instant.now();
        String from = now.minus(1, ChronoUnit.HOURS).toString();

        mockMvc.perform(get("/api/sales/summary").header("Authorization", authA).param("from", from))
                .andExpect(jsonPath("$.costTotal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.profit").value(org.hamcrest.Matchers.nullValue()));

        // fixture products have a zero cost price, so profit equals net revenue (10.00 + 20.00)
        mockMvc.perform(get("/api/sales/summary").header("Authorization", managerAuth)
                        .param("from", from))
                .andExpect(jsonPath("$.costTotal", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.profit", org.hamcrest.Matchers.notNullValue()));
        mockMvc.perform(get("/api/sales/summary").header("Authorization", adminAuth).param("from", from))
                .andExpect(jsonPath("$.profit", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void unknownSalesAreNotFound() throws Exception {
        client.get(managerAuth, "/api/sales/{id}", java.util.UUID.randomUUID()).andExpect(status().isNotFound());
        client.get(managerAuth, "/api/sales/receipt/{n}", "S00000000-000000").andExpect(status().isNotFound());
    }
}
