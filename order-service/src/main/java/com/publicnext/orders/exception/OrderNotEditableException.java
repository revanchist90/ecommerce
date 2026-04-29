package com.publicnext.orders.exception;

import com.publicnext.orders.domain.OrderStatus;

import java.util.UUID;

public class OrderNotEditableException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus status;

    public OrderNotEditableException(UUID orderId, OrderStatus status) {
        super("Order %s cannot be edited in status %s".formatted(orderId, status));
        this.orderId = orderId;
        this.status = status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
