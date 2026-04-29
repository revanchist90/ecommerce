package com.publicnext.orders.dto.event;

import com.publicnext.orders.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderDeletedPayload(
        UUID orderId,
        OrderStatus statusAtDeletion,
        Instant deletedAt
) {
}
