package com.pos.auth.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.pos.auth.entity.Role;
import com.pos.auth.repository.UserRepository;
import com.pos.auth.security.JwtService;
import com.pos.support.TestAccounts;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Covers the two login modes (email+password for managers/admins, PIN+register code for
 * cashiers on shared terminals) and fast user switching via {@code /api/auth/switch-user}.
 * Accounts are created per test run by {@link TestAccounts}; only the REG-1 register comes
 * from the V14 seed migration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

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

    @Value("${pos.jwt.secret}")
    private String jwtSecret;

    private TestAccounts.Account manager;
    private TestAccounts.Account admin;
    private TestAccounts.Account cashier;

    @BeforeEach
    void createAccounts() {
        TestAccounts accounts = new TestAccounts(userRepository, passwordEncoder);
        manager = accounts.create(Role.MANAGER);
        admin = accounts.create(Role.ADMIN);
        cashier = accounts.create(Role.CASHIER);
    }

    @Test
    void emailPasswordLoginSucceedsForManager() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(manager.email(), manager.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role", equalTo("MANAGER")))
                .andExpect(jsonPath("$.registerId", nullValue()));
    }

    @Test
    void emailPasswordLoginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(manager.email(), "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginSucceedsForCashierAndCarriesRegisterId() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinCredentials(cashier.pin(), "REG-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role", equalTo("CASHIER")))
                .andExpect(jsonPath("$.registerId", notNullValue()));
    }

    @Test
    void pinLoginRejectsWrongPin() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinCredentials("0099", "REG-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginRejectsUnknownRegister() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinCredentials(cashier.pin(), "NOPE")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginRejectsAMalformedPin() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"12\", \"registerCode\": \"REG-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchUserKeepsTheSameRegisterSessionActive() throws Exception {
        // register seeded by V14__seed_dev_data.sql
        String seedRegisterId = "11111111-1111-1111-1111-111111111111";

        String cashierToken = pinLogin(cashier.pin(), "REG-1");

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"%s\"}".formatted(manager.pin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role", equalTo("MANAGER")))
                .andExpect(jsonPath("$.registerId", equalTo(seedRegisterId)));
    }

    @Test
    void switchUserRequiresAnActiveRegisterSession() throws Exception {
        String adminToken = login(admin.email(), admin.password());

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"%s\"}".formatted(cashier.pin())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchUserRejectsAnUnknownPin() throws Exception {
        String cashierToken = pinLogin(cashier.pin(), "REG-1");

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"0099\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheAuthenticatedUser() throws Exception {
        String token = login(manager.email(), manager.password());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo(manager.email())))
                .andExpect(jsonPath("$.role", equalTo("MANAGER")));
    }

    @Test
    void meWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageTokenIsRejectedAsInvalid() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", equalTo("Invalid authentication token")));
    }

    @Test
    void anExpiredTokenIsRejectedWithAnExplicitMessage() throws Exception {
        // signed with the application's own key, but already past its expiry
        String expired = new JwtService(jwtSecret, -1_000).generateToken(manager.user(), null);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", equalTo("Your session has expired. Please sign in again.")));
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private static String pinCredentials(String pin, String registerCode) {
        return "{\"pin\": \"%s\", \"registerCode\": \"%s\"}".formatted(pin, registerCode);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String pinLogin(String pin, String registerCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"%s\", \"registerCode\": \"%s\"}".formatted(pin, registerCode)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
