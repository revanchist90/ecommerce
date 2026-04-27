package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(UUID orderId);
}
