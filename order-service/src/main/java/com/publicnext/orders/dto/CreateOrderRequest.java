package com.publicnext.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty @Valid List<LineRequest> orderLines
) {
    public record LineRequest(
            @NotNull UUID productId,
            @NotNull @Min(1) Integer quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice
    ) {}
}
