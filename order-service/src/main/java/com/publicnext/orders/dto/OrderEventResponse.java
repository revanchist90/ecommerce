package com.publicnext.orders.dto;

import com.publicnext.orders.domain.OrderEvent;
import com.publicnext.orders.domain.OrderEventType;
import com.publicnext.orders.domain.OrderStatus;

import java.time.Instant;

public record OrderEventResponse(
        OrderEventType eventType,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        Instant occurredAt
) {
    public static OrderEventResponse from(OrderEvent event) {
        return new OrderEventResponse(
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getOccurredAt()
        );
    }
}
