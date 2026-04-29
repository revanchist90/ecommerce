package com.publicnext.orders.dto.event;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderUpdatedPayload(
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<Line> orderLines,
        Instant updatedAt
) {
    public static OrderUpdatedPayload from(Order order) {
        List<Line> lines = order.getOrderLines().stream()
                .map(Line::from)
                .toList();
        return new OrderUpdatedPayload(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                lines,
                order.getUpdatedAt()
        );
    }

    public record Line(UUID productId, Integer quantity, BigDecimal unitPrice) {
        public static Line from(OrderLine line) {
            return new Line(line.getProductId(), line.getQuantity(), line.getUnitPrice());
        }
    }
}
