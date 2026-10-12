package com.pos.catalog.web;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.pos.support.ApiAuth;
import com.pos.support.TestAccounts;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CategoryApiIntegrationTest {

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

    private String managerAuth;
    private String cashierAuth;

    @BeforeEach
    void login() throws Exception {
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        managerAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.MANAGER));
        cashierAuth = ApiAuth.bearer(mockMvc, objectMapper, accounts.create(Role.CASHIER));
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCanReadButNotWrite() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", cashierAuth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/categories").header("Authorization", cashierAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(uniqueName(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCreatesViewsAndUpdatesACategory() throws Exception {
        String name = uniqueName();
        String id = createCategory(name, "Fresh things");

        mockMvc.perform(get("/api/categories/{id}", id).header("Authorization", cashierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo(name)))
                .andExpect(jsonPath("$.description", equalTo("Fresh things")))
                .andExpect(jsonPath("$.active", equalTo(true)));

        String renamed = uniqueName();
        mockMvc.perform(put("/api/categories/{id}", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(renamed, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo(renamed)));
    }

    @Test
    void duplicateNamesAreRejectedCaseInsensitively() throws Exception {
        String name = uniqueName();
        createCategory(name, null);

        mockMvc.perform(post("/api/categories").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name.toUpperCase(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void blankNameIsRejectedWithAFieldError() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body("  ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void categoriesCanBeDeactivatedAndFilteredByStatus() throws Exception {
        String name = uniqueName();
        String id = createCategory(name, null);

        mockMvc.perform(patch("/api/categories/{id}/status", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));

        mockMvc.perform(get("/api/categories").header("Authorization", cashierAuth)
                        .param("active", "false").param("search", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
        mockMvc.perform(get("/api/categories").header("Authorization", cashierAuth)
                        .param("active", "true").param("search", name))
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void unknownCategoryIsNotFound() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", UUID.randomUUID()).header("Authorization", cashierAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusPatchRequiresTheActiveField() throws Exception {
        String id = createCategory(uniqueName(), null);
        mockMvc.perform(patch("/api/categories/{id}/status", id).header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String createCategory(String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories").header("Authorization", managerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String body(String name, String description) {
        return description == null
                ? "{\"name\": \"%s\"}".formatted(name)
                : "{\"name\": \"%s\", \"description\": \"%s\"}".formatted(name, description);
    }

    private static String uniqueName() {
        return "Cat-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
