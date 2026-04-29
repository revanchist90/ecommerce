package com.publicnext.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.orders.domain.OutboxEvent;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.UpdateOrderRequest;
import com.publicnext.orders.repository.OutboxRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private OutboxRepository outboxRepository;

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

    @Test
    void updateOrder_replacesLines_andWritesOutboxRow() throws Exception {
        UUID customerId = UUID.randomUUID();

        CreateOrderRequest createRequest = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 1, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID orderId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText());

        UUID newCustomer = UUID.randomUUID();
        UpdateOrderRequest updateRequest = new UpdateOrderRequest(
                newCustomer,
                List.of(
                        new UpdateOrderRequest.LineRequest(PRODUCT_100, 2, new BigDecimal("10.00")),
                        new UpdateOrderRequest.LineRequest(PRODUCT_50,  3, new BigDecimal("7.50"))
                )
        );

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(newCustomer.toString()))
                .andExpect(jsonPath("$.totalAmount").value(42.50))
                .andExpect(jsonPath("$.orderLines", org.hamcrest.Matchers.hasSize(2)));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(newCustomer.toString()))
                .andExpect(jsonPath("$.totalAmount").value(42.50))
                .andExpect(jsonPath("$.orderLines", org.hamcrest.Matchers.hasSize(2)));

        List<OutboxEvent> rows = outboxRepository.findByAggregateIdOrderByCreatedAtAsc(orderId);
        assertThat(rows).extracting(OutboxEvent::getEventType)
                .containsExactly("ORDER_CREATED", "ORDER_UPDATED");

        JsonNode updatedPayload = objectMapper.readTree(rows.get(1).getPayload());
        assertThat(updatedPayload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(updatedPayload.get("customerId").asText()).isEqualTo(newCustomer.toString());
        assertThat(updatedPayload.get("orderLines").size()).isEqualTo(2);
    }

    @Test
    void updateOrder_afterTransition_returns409() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 1, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText();

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk());

        UpdateOrderRequest updateRequest = new UpdateOrderRequest(
                UUID.randomUUID(),
                List.of(new UpdateOrderRequest.LineRequest(PRODUCT_50, 1, new BigDecimal("7.50")))
        );

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PROCESSING")));
    }

    @Test
    void listOrders_filtersByCustomerAndStatus_andPaginates() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();

        createOrderFor(customerA, PRODUCT_100, 1, "10.00");
        createOrderFor(customerA, PRODUCT_50,  2, "7.50");
        createOrderFor(customerB, PRODUCT_100, 1, "10.00");

        // Customer A unfiltered: 2 orders, both UNPROCESSED
        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customerA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.content[*].customerId",
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo(customerA.toString()))));

        // Status filter excludes everything since none are PROCESSING
        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customerA.toString())
                        .param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Pagination: page 0 size 1 yields 1 item with totalPages computed across customer A's 2 orders
        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customerA.toString())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listOrders_excludesSoftDeletedOrders() throws Exception {
        UUID customer = UUID.randomUUID();
        String keptId = createOrderFor(customer, PRODUCT_100, 1, "10.00");
        String deletedId = createOrderFor(customer, PRODUCT_50, 1, "7.50");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/orders/{orderId}", deletedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customer.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(keptId));
    }

    private String createOrderFor(UUID customerId, UUID productId, int qty, String unitPrice) throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderRequest.LineRequest(productId, qty, new BigDecimal(unitPrice)))
        );
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText();
    }

    @Test
    void createAndTransition_writesOutboxRows_unpublished() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 1, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID orderId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText());

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk());

        List<OutboxEvent> rows = outboxRepository.findByAggregateIdOrderByCreatedAtAsc(orderId);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OutboxEvent::getEventType)
                .containsExactly("ORDER_CREATED", "STATUS_CHANGED");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getAggregateType()).isEqualTo("ORDER");
            assertThat(row.getPayload()).isNotBlank();
            assertThat(row.getPublishedAt()).isNull();
            assertThat(row.getAttempts()).isZero();
        });

        JsonNode createdPayload = objectMapper.readTree(rows.get(0).getPayload());
        assertThat(createdPayload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(createdPayload.get("status").asText()).isEqualTo("UNPROCESSED");

        JsonNode transitionedPayload = objectMapper.readTree(rows.get(1).getPayload());
        assertThat(transitionedPayload.get("fromStatus").asText()).isEqualTo("UNPROCESSED");
        assertThat(transitionedPayload.get("toStatus").asText()).isEqualTo("PROCESSING");
    }
}
