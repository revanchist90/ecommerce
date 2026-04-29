package com.publicnext.orders.controller;

import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.ErrorResponse;
import com.publicnext.orders.dto.OrderEventResponse;
import com.publicnext.orders.dto.OrderListFilter;
import com.publicnext.orders.dto.OrderResponse;
import com.publicnext.orders.dto.PagedResponse;
import com.publicnext.orders.dto.UpdateOrderRequest;
import com.publicnext.orders.dto.UpdateOrderStatusRequest;
import com.publicnext.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new order")
    @ApiResponse(responseCode = "201", description = "Order created")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    @Operation(summary = "List orders with optional filters and pagination")
    @ApiResponse(responseCode = "200", description = "Page of orders")
    public PagedResponse<OrderResponse> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @PageableDefault(size = 20) Pageable pageable) {
        OrderListFilter filter = new OrderListFilter(status, customerId, createdFrom, createdTo);
        return orderService.listOrders(filter, pageable);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Retrieve an order by its public ID")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderService.getByOrderId(orderId);
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Replace an order's customer and lines (only while UNPROCESSED)")
    @ApiResponse(responseCode = "200", description = "Order updated")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Order not editable or insufficient stock",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderResponse updateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.updateOrder(orderId, request);
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Transition an order to the next status")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Invalid status transition",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderResponse updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.transitionStatus(orderId, request.status());
    }

    @GetMapping("/{orderId}/history")
    @Operation(summary = "Retrieve the audit history for an order")
    @ApiResponse(responseCode = "200", description = "History retrieved")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<OrderEventResponse> getHistory(@PathVariable UUID orderId) {
        return orderService.getHistory(orderId);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an order")
    @ApiResponse(responseCode = "204", description = "Order soft-deleted")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void deleteOrder(@PathVariable UUID orderId) {
        orderService.softDelete(orderId);
    }
}
