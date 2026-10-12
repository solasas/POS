package com.pos.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Covers catalog CRUD, role restrictions and input validation. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // seeded by V15__seed_catalog.sql
    private static final String SEED_CATEGORY_ID = "aaaaaaaa-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anyAuthenticatedUserCanListProducts() throws Exception {
        mockMvc.perform(get("/api/catalog/products").header("Authorization", "Bearer " + cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void listingProductsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCannotCreateProducts() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .header("Authorization", "Bearer " + cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), "Thing", "1.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanCreateUpdateAndDeleteAProduct() throws Exception {
        String token = supervisorToken();
        String sku = uniqueSku();

        MvcResult created = mockMvc.perform(post("/api/catalog/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "Test Item", "2.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.categoryName").value("Produce"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/catalog/products/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "Renamed Item", "3.75")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Item"))
                .andExpect(jsonPath("$.price").value(3.75));

        mockMvc.perform(delete("/api/catalog/products/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/catalog/products/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateSkuIsRejectedWithConflict() throws Exception {
        String token = supervisorToken();
        String sku = uniqueSku();
        String body = productJson(sku, "First", "1.00");

        mockMvc.perform(post("/api/catalog/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/catalog/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void negativePriceIsRejected() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), "Bad Price", "-1.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), " ", "1.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCategoryIsRejected() throws Exception {
        String body = productJson(uniqueSku(), "Orphan", "1.00").replace(SEED_CATEGORY_ID, UUID.randomUUID().toString());
        mockMvc.perform(post("/api/catalog/products")
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/catalog/categories/{id}", SEED_CATEGORY_ID)
                        .header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void supervisorCanCreateAndDeleteAnEmptyCategory() throws Exception {
        String token = supervisorToken();
        MvcResult created = mockMvc.perform(post("/api/catalog/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Cat-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/catalog/categories/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void cashierCannotCreateCategories() throws Exception {
        mockMvc.perform(post("/api/catalog/categories")
                        .header("Authorization", "Bearer " + cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    private static String uniqueSku() {
        return "T-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static String productJson(String sku, String name, String price) {
        return """
                {"sku": "%s", "name": "%s", "categoryId": "%s", "unitType": "EACH",
                 "price": %s, "taxClass": "STANDARD", "ageRestricted": false, "reorderPoint": 0}
                """.formatted(sku, name, SEED_CATEGORY_ID, price);
    }

    private String supervisorToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"supervisor@pos.local\", \"password\": \"Supervisor#12345\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String cashierToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"2222\", \"registerCode\": \"REG-1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
