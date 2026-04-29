package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.OrderListFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> matching(OrderListFilter filter) {
        Specification<Order> spec = Specification.where(null);
        if (filter == null) {
            return spec;
        }
        if (filter.status() != null) {
            spec = spec.and(hasStatus(filter.status()));
        }
        if (filter.customerId() != null) {
            spec = spec.and(hasCustomerId(filter.customerId()));
        }
        if (filter.createdFrom() != null) {
            spec = spec.and(createdAtFrom(filter.createdFrom()));
        }
        if (filter.createdTo() != null) {
            spec = spec.and(createdAtTo(filter.createdTo()));
        }
        return spec;
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> createdAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdAtTo(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
