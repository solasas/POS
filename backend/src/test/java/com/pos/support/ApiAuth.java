package com.pos.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Logs a {@link TestAccounts.Account} in through the real endpoint and returns an Authorization header value. */
public final class ApiAuth {

    private ApiAuth() {
    }

    public static String bearer(MockMvc mockMvc, ObjectMapper objectMapper, TestAccounts.Account account)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}"
                                .formatted(account.email(), account.password())))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
