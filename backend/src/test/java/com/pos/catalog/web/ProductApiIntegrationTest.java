package com.pos.catalog.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.auth.entity.Role;
import com.pos.auth.repository.UserRepository;
import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.UnitOfMeasure;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.support.ApiAuth;
import com.pos.support.TestAccounts;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductApiIntegrationTest {

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

    private String managerAuth;
    private String cashierAuth;
    private Category category;

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.MANAGER));
        cashierAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.CASHIER));
        category = categoryRepository.save(Category.builder().name("Cat-" + token()).build());
    }

    // ---- authorization -------------------------------------------------------

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCanReadButNotCreateOrChangeProducts() throws Exception {
        String id = createProduct(token(), null, "Item", "1.00");

        mockMvc.perform(get("/api/products/{id}", id).header("Authorization", cashierAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products").header("Authorization", cashierAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(token(), null, "Item", "1.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/products/{id}/status", id).header("Authorization", cashierAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentIsPublicAndDescribesTheCatalogEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/categories/{id}/status']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me']").exists());
    }

    // ---- create / validation -------------------------------------------------

    @Test
    void createdProductsStartWithZeroStockAndANormalizedSku() throws Exception {
        String sku = "milk-" + token();
        MvcResult result = mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(sku, "1234567890123", "Milk", "1.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", equalTo(sku.toUpperCase())))
                .andExpect(jsonPath("$.stockQuantity", equalTo(0)))
                .andExpect(jsonPath("$.active", equalTo(true)))
                .andExpect(jsonPath("$.categoryName", equalTo(category.getName())))
                .andReturn();
        assertBodyHasNo(result, "\"taxClass\"");
    }

    @Test
    void duplicateSkuIsRejectedIgnoringCase() throws Exception {
        String sku = "dup-" + token();
        createProduct(sku, null, "First", "1.00");

        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(sku.toUpperCase(), null, "Second", "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void theDatabaseRejectsSkusThatDifferOnlyByCase() {
        String sku = "DB-" + token().toUpperCase();
        productRepository.saveAndFlush(Product.builder().sku(sku).name("A").category(category)
                .unit(UnitOfMeasure.PIECE).price(BigDecimal.ONE).build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        productRepository.saveAndFlush(Product.builder().sku(sku.toLowerCase()).name("B")
                                .category(category).unit(UnitOfMeasure.PIECE).price(BigDecimal.ONE).build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void duplicateBarcodeIsRejected() throws Exception {
        String barcode = "9" + token().replaceAll("[^0-9]", "0");
        createProduct(token(), barcode, "First", "1.00");

        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(token(), barcode, "Second", "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.barcode").exists());
    }

    @Test
    void severalProductsMayOmitTheBarcode() throws Exception {
        createProduct(token(), null, "No barcode A", "1.00");
        createProduct(token(), "", "No barcode B", "1.00");
    }

    @Test
    void invalidFieldsAreReportedPerField() throws Exception {
        String body = """
                {"sku": "bad sku!", "name": " ", "categoryId": "%s", "unit": "PIECE",
                 "price": 0, "costPrice": -1, "lowStockThreshold": -2}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.costPrice").exists())
                .andExpect(jsonPath("$.fieldErrors.lowStockThreshold").exists());
    }

    @Test
    void priceWithTooManyDecimalsIsRejected() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(token(), null, "Item", "1.999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void unknownCategoryIsRejected() throws Exception {
        String body = json(token(), null, "Orphan", "1.00").replace(category.getId().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
    }

    @Test
    void inactiveCategoryCannotReceiveNewProducts() throws Exception {
        category.setActive(false);
        categoryRepository.save(category);

        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(token(), null, "Item", "1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ---- update / status / lookup -------------------------------------------

    @Test
    void updateChangesFieldsButNeverStock() throws Exception {
        Product stocked = productRepository.save(Product.builder()
                .sku("STK-" + token().toUpperCase()).name("Stocked").category(category)
                .unit(UnitOfMeasure.PIECE).price(new BigDecimal("2.00"))
                .stockQuantity(new BigDecimal("12.000")).build());

        mockMvc.perform(put("/api/products/{id}", stocked.getId()).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stocked.getSku(), null, "Renamed", "3.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Renamed")))
                .andExpect(jsonPath("$.price", equalTo(3.5)))
                .andExpect(jsonPath("$.stockQuantity", equalTo(12.0)));
    }

    @Test
    void updateCannotTakeAnotherProductsSku() throws Exception {
        String otherSku = "oth-" + token();
        createProduct(otherSku, null, "Other", "1.00");
        String id = createProduct(token(), null, "Mine", "1.00");

        mockMvc.perform(put("/api/products/{id}", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(otherSku, null, "Mine", "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void productsCanBeDeactivatedAndReactivated() throws Exception {
        String id = createProduct(token(), null, "Toggle", "1.00");

        mockMvc.perform(patch("/api/products/{id}/status", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));
        mockMvc.perform(patch("/api/products/{id}/status", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": true}"))
                .andExpect(jsonPath("$.active", equalTo(true)));
    }

    @Test
    void barcodeLookupFindsTheProductOrReturns404() throws Exception {
        String barcode = "8" + token().replaceAll("[^0-9]", "1");
        String id = createProduct(token(), barcode, "Scanned", "1.00");

        mockMvc.perform(get("/api/products/barcode/{barcode}", barcode).header("Authorization", cashierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(id)));
        mockMvc.perform(get("/api/products/barcode/{barcode}", "0000000000000").header("Authorization", cashierAuth))
                .andExpect(status().isNotFound());
    }

    // ---- search / filter / pagination ---------------------------------------

    @Test
    void searchMatchesNameSkuAndBarcode() throws Exception {
        String marker = token();
        String barcode = "7" + marker.replaceAll("[^0-9]", "2");
        createProduct("sku-" + marker, null, "Blueberry " + marker, "1.00");
        createProduct("zz-" + token(), barcode, "Plain", "1.00");

        search(marker).andExpect(jsonPath("$.totalElements", equalTo(1)));          // name or sku
        search(barcode).andExpect(jsonPath("$.totalElements", equalTo(1)));         // barcode
        search("SKU-" + marker.toUpperCase()).andExpect(jsonPath("$.totalElements", equalTo(1))); // case-insensitive
        search("no-such-" + token()).andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void searchTreatsLikeWildcardsLiterally() throws Exception {
        createProduct(token(), null, "Plain item", "1.00");

        search("%").andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void filtersByCategoryAndActiveStatus() throws Exception {
        String activeId = createProduct(token(), null, "Active one", "1.00");
        String inactiveId = createProduct(token(), null, "Inactive one", "1.00");
        mockMvc.perform(patch("/api/products/{id}/status", inactiveId).header("Authorization", managerAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"));

        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth)
                        .param("categoryId", category.getId().toString()))
                .andExpect(jsonPath("$.totalElements", equalTo(2)));
        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth)
                        .param("categoryId", category.getId().toString()).param("active", "false"))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(inactiveId)));
        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth)
                        .param("categoryId", category.getId().toString()).param("active", "true"))
                .andExpect(jsonPath("$.content[0].id", equalTo(activeId)));
    }

    @Test
    void lowStockFlagAndFilterFollowTheThreshold() throws Exception {
        Product low = saveStocked("LOW-" + token().toUpperCase(), "2", "5");
        Product healthy = saveStocked("OK-" + token().toUpperCase(), "50", "5");

        mockMvc.perform(get("/api/products/{id}", low.getId()).header("Authorization", cashierAuth))
                .andExpect(jsonPath("$.lowStock", equalTo(true)));
        mockMvc.perform(get("/api/products/{id}", healthy.getId()).header("Authorization", cashierAuth))
                .andExpect(jsonPath("$.lowStock", equalTo(false)));

        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth)
                        .param("categoryId", category.getId().toString()).param("lowStock", "true"))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(low.getId().toString())));
    }

    @Test
    void paginatesAndSorts() throws Exception {
        for (int price = 1; price <= 5; price++) {
            createProduct(token(), null, "Paged " + price, price + ".00");
        }

        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth)
                        .param("categoryId", category.getId().toString())
                        .param("sortBy", "price").param("direction", "desc")
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(5)))
                .andExpect(jsonPath("$.totalPages", equalTo(3)))
                .andExpect(jsonPath("$.page", equalTo(1)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].price", equalTo(3.0)))
                .andExpect(jsonPath("$.content[1].price", equalTo(2.0)));
    }

    @Test
    void pageSizeIsCappedAndBadSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(100)));
        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth).param("sortBy", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sortBy").exists());
        mockMvc.perform(get("/api/products").header("Authorization", cashierAuth).param("direction", "sideways"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers -------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions search(String term) throws Exception {
        return mockMvc.perform(get("/api/products").header("Authorization", cashierAuth).param("search", term))
                .andExpect(status().isOk());
    }

    private Product saveStocked(String sku, String stock, String threshold) {
        return productRepository.save(Product.builder()
                .sku(sku).name("Stocked " + sku).category(category).unit(UnitOfMeasure.PIECE)
                .price(new BigDecimal("1.00"))
                .stockQuantity(new BigDecimal(stock)).lowStockThreshold(new BigDecimal(threshold))
                .build());
    }

    private String createProduct(String sku, String barcode, String name, String price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(sku, barcode, name, price)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String json(String sku, String barcode, String name, String price) {
        String barcodeField = barcode == null ? "null" : "\"" + barcode + "\"";
        return """
                {"sku": "%s", "barcode": %s, "name": "%s", "categoryId": "%s", "unit": "PIECE",
                 "price": %s, "costPrice": 0.50, "lowStockThreshold": 5}
                """.formatted(sku, barcodeField, name, category.getId(), price);
    }

    private static void assertBodyHasNo(MvcResult result, String fragment) throws Exception {
        if (result.getResponse().getContentAsString().contains(fragment)) {
            throw new AssertionError("Response unexpectedly contains " + fragment);
        }
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
