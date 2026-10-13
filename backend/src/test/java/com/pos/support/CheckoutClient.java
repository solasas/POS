package com.pos.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin helper around MockMvc for the sales endpoints. */
public class CheckoutClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public CheckoutClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Builds a checkout body: {@code lines} alternates product id and quantity. */
    public static String body(String method, String tendered, String discount, Object... lines) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < lines.length; i += 2) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"productId\":\"%s\",\"quantity\":%s}".formatted(lines[i], lines[i + 1]));
        }
        return "{\"items\":[%s],%s\"payment\":{\"method\":\"%s\"%s}}".formatted(
                items,
                discount == null ? "" : "\"discountPercent\":" + discount + ",",
                method,
                tendered == null ? "" : ",\"amountTendered\":" + tendered);
    }

    public static String cash(String tendered, Object... lines) {
        return body("CASH", tendered, null, lines);
    }

    public ResultActions checkout(String auth, String key, String json) throws Exception {
        var request = post("/api/sales/checkout")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    public ResultActions checkout(String auth, String json) throws Exception {
        return checkout(auth, "key-" + UUID.randomUUID(), json);
    }

    public ResultActions get(String auth, String url, Object... vars) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(url, vars).header("Authorization", auth));
    }

    public JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    public static String newKey() {
        return "k-" + UUID.randomUUID();
    }
}
