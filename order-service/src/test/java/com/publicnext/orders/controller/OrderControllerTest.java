package com.publicnext.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.orders.domain.OrderEventType;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.OrderEventResponse;
import com.publicnext.orders.dto.OrderListFilter;
import com.publicnext.orders.dto.OrderResponse;
import com.publicnext.orders.dto.PagedResponse;
import com.publicnext.orders.dto.UpdateOrderRequest;
import com.publicnext.orders.exception.InsufficientStockException;
import com.publicnext.orders.exception.InvalidStatusTransitionException;
import com.publicnext.orders.exception.OrderNotEditableException;
import com.publicnext.orders.exception.OrderNotFoundException;
import com.publicnext.orders.config.SecurityConfig;
import com.publicnext.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void createOrder_validRequest_returns201() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderRequest.LineRequest(productId, 2, new BigDecimal("10.00")))
        );

        OrderResponse response = new OrderResponse(
                orderId, customerId, OrderStatus.UNPROCESSED, Instant.now(),
                new BigDecimal("20.00"), List.of(), Instant.now(), Instant.now()
        );

        when(orderService.createOrder(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("UNPROCESSED"))
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void createOrder_missingCustomerId_returns400() throws Exception {
        String body = """
                {
                  "orderLines": [
                    { "productId": "%s", "quantity": 1, "unitPrice": 5.00 }
                  ]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("customerId")));
    }

    @Test
    void createOrder_emptyOrderLines_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("orderLines")));
    }

    @Test
    void createOrder_negativeQuantity_returns400() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "orderLines": [
                    { "productId": "%s", "quantity": -1, "unitPrice": 5.00 }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    void listOrders_returnsPageWithFilters() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderResponse content = new OrderResponse(
                orderId, customerId, OrderStatus.PROCESSING, Instant.now(),
                new BigDecimal("10.00"), List.of(), Instant.now(), Instant.now()
        );
        PagedResponse<OrderResponse> page = new PagedResponse<>(List.of(content), 0, 20, 1L, 1);

        when(orderService.listOrders(
                argThat((OrderListFilter f) -> f != null
                        && f.status() == OrderStatus.PROCESSING
                        && customerId.equals(f.customerId())),
                any())
        ).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "PROCESSING")
                        .param("customerId", customerId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listOrders_noFilters_passesNullFilterFields() throws Exception {
        PagedResponse<OrderResponse> empty = new PagedResponse<>(List.of(), 0, 20, 0L, 0);

        when(orderService.listOrders(
                argThat((OrderListFilter f) -> f != null
                        && f.status() == null
                        && f.customerId() == null
                        && f.createdFrom() == null
                        && f.createdTo() == null),
                any())
        ).thenReturn(empty);

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getOrder_existing_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, customerId, OrderStatus.PROCESSING, Instant.now(),
                new BigDecimal("99.00"), List.of(), Instant.now(), Instant.now()
        );

        when(orderService.getByOrderId(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void createOrder_insufficientStock_returns409() throws Exception {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(productId, 100, new BigDecimal("10.00")))
        );

        when(orderService.createOrder(any())).thenThrow(new InsufficientStockException(
                List.of(new InsufficientStockException.Detail(productId, 100, 5))
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("requested 100, available 5")));
    }

    @Test
    void getOrder_missing_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getByOrderId(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(orderId.toString())));
    }

    @Test
    void updateStatus_validTransition_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, UUID.randomUUID(), OrderStatus.PROCESSING, Instant.now(),
                new BigDecimal("20.00"), List.of(), Instant.now(), Instant.now()
        );

        when(orderService.transitionStatus(eq(orderId), eq(OrderStatus.PROCESSING))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void updateStatus_invalidTransition_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.transitionStatus(any(), any()))
                .thenThrow(new InvalidStatusTransitionException(
                        orderId, OrderStatus.UNPROCESSED, OrderStatus.PROCESSED));

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("UNPROCESSED -> PROCESSED")));
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("status")));
    }

    @Test
    void updateStatus_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.transitionStatus(any(), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateOrder_validRequest_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        UpdateOrderRequest request = new UpdateOrderRequest(
                customerId,
                List.of(new UpdateOrderRequest.LineRequest(productId, 2, new BigDecimal("10.00")))
        );

        OrderResponse response = new OrderResponse(
                orderId, customerId, OrderStatus.UNPROCESSED, Instant.now(),
                new BigDecimal("20.00"), List.of(), Instant.now(), Instant.now()
        );

        when(orderService.updateOrder(eq(orderId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void updateOrder_emptyOrderLines_returns400() throws Exception {
        UpdateOrderRequest request = new UpdateOrderRequest(UUID.randomUUID(), List.of());

        mockMvc.perform(put("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("orderLines")));
    }

    @Test
    void updateOrder_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        UpdateOrderRequest request = new UpdateOrderRequest(
                UUID.randomUUID(),
                List.of(new UpdateOrderRequest.LineRequest(UUID.randomUUID(), 1, new BigDecimal("5.00")))
        );

        when(orderService.updateOrder(eq(orderId), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateOrder_orderNotEditable_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        UpdateOrderRequest request = new UpdateOrderRequest(
                UUID.randomUUID(),
                List.of(new UpdateOrderRequest.LineRequest(UUID.randomUUID(), 1, new BigDecimal("5.00")))
        );

        when(orderService.updateOrder(eq(orderId), any()))
                .thenThrow(new OrderNotEditableException(orderId, OrderStatus.PROCESSING));

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PROCESSING")));
    }

    @Test
    void updateOrder_insufficientStock_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UpdateOrderRequest request = new UpdateOrderRequest(
                UUID.randomUUID(),
                List.of(new UpdateOrderRequest.LineRequest(productId, 100, new BigDecimal("5.00")))
        );

        when(orderService.updateOrder(eq(orderId), any()))
                .thenThrow(new InsufficientStockException(
                        List.of(new InsufficientStockException.Detail(productId, 100, 3))));

        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("requested 100, available 3")));
    }

    @Test
    void getHistory_returns200WithEvents() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getHistory(orderId)).thenReturn(List.of(
                new OrderEventResponse(OrderEventType.ORDER_CREATED, null,
                        OrderStatus.UNPROCESSED, Instant.parse("2026-01-01T10:00:00Z")),
                new OrderEventResponse(OrderEventType.STATUS_CHANGED, OrderStatus.UNPROCESSED,
                        OrderStatus.PROCESSING, Instant.parse("2026-01-01T10:05:00Z"))
        ));

        mockMvc.perform(get("/api/v1/orders/{orderId}/history", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].eventType").value("ORDER_CREATED"))
                .andExpect(jsonPath("$[0].fromStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].toStatus").value("UNPROCESSED"))
                .andExpect(jsonPath("$[1].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].fromStatus").value("UNPROCESSED"))
                .andExpect(jsonPath("$[1].toStatus").value("PROCESSING"));
    }

    @Test
    void getHistory_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getHistory(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}/history", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
