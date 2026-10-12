package com.pos.sales.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.registers.entity.Register;
import com.pos.registers.repository.RegisterRepository;
import com.pos.sales.entity.Transaction;
import com.pos.sales.entity.TransactionStatus;
import com.pos.sales.repository.TransactionRepository;

/**
 * Covers the role-escalation rules on {@link TransactionController}: only SUPERVISOR+ may void a
 * COMPLETED sale or approve a discount above 15%, and ADMIN inherits SUPERVISOR permissions via
 * the configured role hierarchy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionAuthorizationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // seeded by V14__seed_dev_data.sql
    private static final UUID SEED_REGISTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEED_CASHIER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RegisterRepository registerRepository;

    private Transaction completedTransaction;
    private Transaction openTransaction;

    @BeforeEach
    void seedTransactions() {
        Register register = registerRepository.findById(SEED_REGISTER_ID).orElseThrow();

        completedTransaction = transactionRepository.save(newTransaction(register, TransactionStatus.COMPLETED));
        openTransaction = transactionRepository.save(newTransaction(register, TransactionStatus.OPEN));
    }

    private Transaction newTransaction(Register register, TransactionStatus status) {
        return Transaction.builder()
                .register(register)
                .cashierId(SEED_CASHIER_ID)
                .status(status)
                .subtotal(new BigDecimal("100.00"))
                .taxTotal(new BigDecimal("5.00"))
                .discountTotal(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("105.00"))
                .build();
    }

    @Test
    void cashierCannotVoidACompletedSale() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/void", completedTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForCashier()))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanVoidACompletedSale() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/void", completedTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForSupervisor()))
                .andExpect(status().isOk());
    }

    @Test
    void adminInheritsSupervisorPermissionsViaRoleHierarchy() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/void", completedTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void voidingATransactionThatIsNotCompletedIsRejected() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/void", openTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForSupervisor()))
                .andExpect(status().isConflict());
    }

    @Test
    void voidWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/void", completedTransaction.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCanApplyADiscountAtOrBelowFifteenPercent() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/discount", openTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForCashier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentage\": 15}"))
                .andExpect(status().isOk());
    }

    @Test
    void cashierCannotApplyADiscountAboveFifteenPercent() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/discount", openTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForCashier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentage\": 15.01}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanApplyADiscountAboveFifteenPercent() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/discount", openTransaction.getId())
                        .header("Authorization", "Bearer " + tokenForSupervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentage\": 40}"))
                .andExpect(status().isOk());
    }

    private String tokenForCashier() throws Exception {
        return pinLogin("2222", "REG-1");
    }

    private String tokenForSupervisor() throws Exception {
        return login("supervisor@pos.local", "Supervisor#12345");
    }

    private String tokenForAdmin() throws Exception {
        return login("admin@pos.local", "Admin#12345");
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
