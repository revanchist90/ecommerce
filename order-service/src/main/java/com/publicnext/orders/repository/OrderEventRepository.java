package com.publicnext.orders.repository;

import com.publicnext.orders.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrder_OrderIdOrderByOccurredAtAsc(UUID orderId);
}
