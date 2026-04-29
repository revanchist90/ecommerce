package com.publicnext.orders.service;

import com.publicnext.orders.config.OrderProgressionProperties;
import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "orders.auto-progression", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderProgressionScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderProgressionProperties properties;

    @Scheduled(fixedDelayString = "${orders.auto-progression.fixed-delay}")
    public void progressOrders() {
        progressBatch(OrderStatus.UNPROCESSED, OrderStatus.PROCESSING, properties.unprocessedToProcessing());
        progressBatch(OrderStatus.PROCESSING, OrderStatus.PROCESSED, properties.processingToProcessed());
        progressBatch(OrderStatus.PROCESSED, OrderStatus.SHIPPED, properties.processedToShipped());
    }

    private void progressBatch(OrderStatus from, OrderStatus to, Duration delay) {
        Instant cutoff = Instant.now().minus(delay);
        List<Order> due = orderRepository.findByStatusAndUpdatedAtBefore(from, cutoff);
        if (due.isEmpty()) {
            return;
        }
        log.debug("Auto-progressing {} order(s) {} -> {}", due.size(), from, to);
        for (Order order : due) {
            try {
                orderService.transitionStatus(order.getOrderId(), to);
            } catch (Exception e) {
                log.warn("Auto-progression failed for order {} ({} -> {}): {}",
                        order.getOrderId(), from, to, e.getMessage());
            }
        }
    }
}
