package com.publicnext.orders.dto;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        Instant orderDate,
        BigDecimal totalAmount,
        List<OrderLineResponse> orderLines,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lines = order.getOrderLines().stream()
                .map(OrderLineResponse::from)
                .toList();
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getOrderDate(),
                order.getTotalAmount(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record OrderLineResponse(
            UUID productId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(
                    line.getProductId(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getLineTotal()
            );
        }
    }
}
