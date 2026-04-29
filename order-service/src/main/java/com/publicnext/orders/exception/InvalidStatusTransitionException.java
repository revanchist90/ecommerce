package com.publicnext.orders.exception;

import com.publicnext.orders.domain.OrderStatus;

import java.util.UUID;

public class InvalidStatusTransitionException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStatusTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Invalid status transition for order %s: %s -> %s".formatted(orderId, from, to));
        this.orderId = orderId;
        this.from = from;
        this.to = to;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
