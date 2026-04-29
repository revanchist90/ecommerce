package com.publicnext.orders.support;

import com.publicnext.orders.domain.Inventory;
import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.UpdateOrderRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderTestData {

    private OrderTestData() {
    }

    public static Order.OrderBuilder anOrder() {
        Instant now = Instant.now();
        return Order.builder()
                .orderId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(OrderStatus.UNPROCESSED)
                .orderDate(now)
                .totalAmount(new BigDecimal("10.00"))
                .createdAt(now)
                .updatedAt(now);
    }

    public static OrderLine.OrderLineBuilder anOrderLine() {
        return OrderLine.builder()
                .productId(UUID.randomUUID())
                .quantity(1)
                .unitPrice(new BigDecimal("10.00"));
    }

    public static CreateOrderRequest aCreateOrderRequest(UUID productId) {
        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(productId, 1, new BigDecimal("10.00")))
        );
    }

    public static UpdateOrderRequest anUpdateOrderRequest(UUID productId) {
        return new UpdateOrderRequest(
                UUID.randomUUID(),
                List.of(new UpdateOrderRequest.LineRequest(productId, 1, new BigDecimal("10.00")))
        );
    }

    public static Inventory anInventory(UUID productId, int availableStock) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setAvailableStock(availableStock);
        return inv;
    }
}
