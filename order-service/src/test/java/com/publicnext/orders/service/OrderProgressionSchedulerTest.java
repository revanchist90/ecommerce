package com.publicnext.orders.service;

import com.publicnext.orders.config.OrderProgressionProperties;
import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProgressionSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private OrderProgressionScheduler scheduler;

    @BeforeEach
    void setUp() {
        OrderProgressionProperties properties = new OrderProgressionProperties(
                true,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                Duration.ofSeconds(90)
        );
        scheduler = new OrderProgressionScheduler(orderRepository, orderService, properties);
    }

    @Test
    void progressOrders_advancesEachStageInOrder() {
        Order o1 = orderWithStatus(OrderStatus.UNPROCESSED);
        Order o2 = orderWithStatus(OrderStatus.PROCESSING);
        Order o3 = orderWithStatus(OrderStatus.PROCESSED);

        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.UNPROCESSED), any()))
                .thenReturn(List.of(o1));
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSING), any()))
                .thenReturn(List.of(o2));
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSED), any()))
                .thenReturn(List.of(o3));

        scheduler.progressOrders();

        verify(orderService).transitionStatus(o1.getOrderId(), OrderStatus.PROCESSING);
        verify(orderService).transitionStatus(o2.getOrderId(), OrderStatus.PROCESSED);
        verify(orderService).transitionStatus(o3.getOrderId(), OrderStatus.SHIPPED);
    }

    @Test
    void progressOrders_appliesConfiguredDelays_toCutoff() {
        when(orderRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        Instant before = Instant.now();
        scheduler.progressOrders();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findByStatusAndUpdatedAtBefore(eq(OrderStatus.UNPROCESSED), cutoffCaptor.capture());
        verify(orderRepository).findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSING), cutoffCaptor.capture());
        verify(orderRepository).findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSED), cutoffCaptor.capture());

        List<Instant> cutoffs = cutoffCaptor.getAllValues();
        assertThat(cutoffs.get(0)).isBetween(before.minusSeconds(30), after.minusSeconds(30));
        assertThat(cutoffs.get(1)).isBetween(before.minusSeconds(60), after.minusSeconds(60));
        assertThat(cutoffs.get(2)).isBetween(before.minusSeconds(90), after.minusSeconds(90));
    }

    @Test
    void progressOrders_continuesWhenIndividualTransitionFails() {
        Order o1 = orderWithStatus(OrderStatus.UNPROCESSED);
        Order o2 = orderWithStatus(OrderStatus.UNPROCESSED);

        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.UNPROCESSED), any()))
                .thenReturn(List.of(o1, o2));
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSING), any()))
                .thenReturn(List.of());
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSED), any()))
                .thenReturn(List.of());

        when(orderService.transitionStatus(o1.getOrderId(), OrderStatus.PROCESSING))
                .thenThrow(new RuntimeException("boom"));

        scheduler.progressOrders();

        verify(orderService).transitionStatus(o1.getOrderId(), OrderStatus.PROCESSING);
        verify(orderService).transitionStatus(o2.getOrderId(), OrderStatus.PROCESSING);
    }

    @Test
    void progressOrders_doesNothingWhenNoneEligible() {
        when(orderRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        scheduler.progressOrders();

        verify(orderService, never()).transitionStatus(any(), any());
    }

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .orderId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(status)
                .orderDate(Instant.now())
                .totalAmount(new BigDecimal("10.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
