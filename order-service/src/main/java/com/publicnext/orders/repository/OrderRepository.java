package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff);
}
