package com.pos.auth.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Covers the two login modes (email+password for supervisors/admins, PIN+register code for
 * cashiers on shared terminals) and fast user switching via {@code /api/auth/switch-user}.
 * Credentials come from the seeded dev data in V14__seed_dev_data.sql: admin@pos.local /
 * Admin#12345 (ADMIN), supervisor@pos.local / Supervisor#12345 + PIN 1111 (SUPERVISOR), and
 * PIN 2222 (CASHIER, no email), all against register REG-1.
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

    @Test
    void emailPasswordLoginSucceedsForSupervisor() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"supervisor@pos.local\", \"password\": \"Supervisor#12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role", equalTo("SUPERVISOR")))
                .andExpect(jsonPath("$.registerId", nullValue()));
    }

    @Test
    void emailPasswordLoginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"supervisor@pos.local\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginSucceedsForCashierAndCarriesRegisterId() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"2222\", \"registerCode\": \"REG-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role", equalTo("CASHIER")))
                .andExpect(jsonPath("$.registerId", notNullValue()));
    }

    @Test
    void pinLoginRejectsWrongPin() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"0099\", \"registerCode\": \"REG-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinLoginRejectsUnknownRegister() throws Exception {
        mockMvc.perform(post("/api/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"2222\", \"registerCode\": \"NOPE\"}"))
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
        // seeded by V14__seed_dev_data.sql
        String seedRegisterId = "11111111-1111-1111-1111-111111111111";

        String cashierToken = pinLogin("2222", "REG-1");

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role", equalTo("SUPERVISOR")))
                .andExpect(jsonPath("$.registerId", equalTo(seedRegisterId)));
    }

    @Test
    void switchUserRequiresAnActiveRegisterSession() throws Exception {
        String adminToken = login("admin@pos.local", "Admin#12345");

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"2222\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchUserRejectsAnUnknownPin() throws Exception {
        String cashierToken = pinLogin("2222", "REG-1");

        mockMvc.perform(post("/api/auth/switch-user")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"0099\"}"))
                .andExpect(status().isUnauthorized());
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
