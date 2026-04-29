package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderEvent;
import com.publicnext.orders.domain.OrderEventType;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.persistence.BaseDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventRepositoryTest extends BaseDataJpaTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Test
    void findByOrderId_returnsEventsInChronologicalOrder() {
        Order order = orderRepository.save(Order.builder()
                .customerId(UUID.randomUUID())
                .status(OrderStatus.UNPROCESSED)
                .orderDate(Instant.now())
                .totalAmount(new BigDecimal("12.34"))
                .build());

        OrderEvent created = newEvent(order, OrderEventType.ORDER_CREATED, null, OrderStatus.UNPROCESSED,
                Instant.parse("2026-01-01T10:00:00Z"));
        OrderEvent transitioned = newEvent(order, OrderEventType.STATUS_CHANGED,
                OrderStatus.UNPROCESSED, OrderStatus.PROCESSING,
                Instant.parse("2026-01-01T10:05:00Z"));

        orderEventRepository.save(transitioned);
        orderEventRepository.save(created);

        List<OrderEvent> events = orderEventRepository.findByOrder_OrderIdOrderByOccurredAtAsc(order.getOrderId());

        assertThat(events).hasSize(2);
        assertThat(events).extracting(OrderEvent::getEventType)
                .containsExactly(OrderEventType.ORDER_CREATED, OrderEventType.STATUS_CHANGED);
        assertThat(events.get(0).getFromStatus()).isNull();
        assertThat(events.get(1).getFromStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(events.get(1).getToStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void findByOrderId_unknownOrder_returnsEmpty() {
        List<OrderEvent> events = orderEventRepository.findByOrder_OrderIdOrderByOccurredAtAsc(UUID.randomUUID());
        assertThat(events).isEmpty();
    }

    private OrderEvent newEvent(Order order, OrderEventType type,
                                OrderStatus from, OrderStatus to, Instant occurredAt) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(type);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setOccurredAt(occurredAt);
        return event;
    }
}
