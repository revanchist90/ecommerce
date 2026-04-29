package com.publicnext.orders.dto;

import com.publicnext.orders.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderListFilter(
        OrderStatus status,
        UUID customerId,
        Instant createdFrom,
        Instant createdTo
) {
}
