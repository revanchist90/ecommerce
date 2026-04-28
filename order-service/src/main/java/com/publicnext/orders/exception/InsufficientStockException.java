package com.publicnext.orders.exception;

import java.util.List;
import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    private final transient List<Detail> details;

    public InsufficientStockException(List<Detail> details) {
        super("Insufficient stock for " + details.size() + " product(s)");
        this.details = List.copyOf(details);
    }

    public List<Detail> getDetails() {
        return details;
    }

    public record Detail(UUID productId, int requested, int available) {}
}
