package com.publicnext.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIntegrationTest extends BaseIntegrationTest {

    // Seeded by Liquibase changeset 004-seed-inventory.sql
    private static final UUID PRODUCT_100 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_50  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT_OUT_OF_STOCK = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenGet_roundtripPersistsAndComputesLineTotals() throws Exception {
        UUID customerId = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(
                        new CreateOrderRequest.LineRequest(PRODUCT_100, 3, new BigDecimal("10.00")),
                        new CreateOrderRequest.LineRequest(PRODUCT_50,  2, new BigDecimal("7.50"))
                )
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.status").value("UNPROCESSED"))
                .andExpect(jsonPath("$.totalAmount").value(45.00))
                .andExpect(jsonPath("$.orderLines", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String orderId = body.get("orderId").asText();

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.totalAmount").value(45.00))
                .andExpect(jsonPath("$.orderLines[?(@.productId=='%s')].lineTotal".formatted(PRODUCT_100))
                        .value(30.00))
                .andExpect(jsonPath("$.orderLines[?(@.productId=='%s')].lineTotal".formatted(PRODUCT_50))
                        .value(15.00));

        assertThat(orderId).isNotBlank();
    }

    @Test
    void getOrder_unknownId_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{orderId}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(unknown.toString())));
    }

    @Test
    void createOrder_invalidBody_returns400FromRealFilterChain() throws Exception {
        String body = """
                {
                  "customerId": null,
                  "orderLines": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void createOrder_outOfStock_returns409() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_OUT_OF_STOCK, 1, new BigDecimal("5.00")))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("requested 1, available 0")));
    }

    @Test
    void createOrder_unknownProductId_returns409() throws Exception {
        UUID unknownProduct = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(unknownProduct, 1, new BigDecimal("5.00")))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("available 0")));
    }

    @Test
    void createTransitionAndGetHistory_recordsBothEvents() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 1, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText();

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/history", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].eventType").value("ORDER_CREATED"))
                .andExpect(jsonPath("$[0].toStatus").value("UNPROCESSED"))
                .andExpect(jsonPath("$[1].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].fromStatus").value("UNPROCESSED"))
                .andExpect(jsonPath("$[1].toStatus").value("PROCESSING"));
    }

    @Test
    void transitionStatus_skippingState_returns409() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 1, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText();

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("UNPROCESSED -> SHIPPED")));
    }

    @Test
    void getHistory_unknownOrder_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/history", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
