package com.publicnext.orders.service;

import com.publicnext.orders.domain.Inventory;
import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderEvent;
import com.publicnext.orders.domain.OrderEventType;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.OrderEventResponse;
import com.publicnext.orders.dto.OrderResponse;
import com.publicnext.orders.exception.InsufficientStockException;
import com.publicnext.orders.exception.InvalidStatusTransitionException;
import com.publicnext.orders.exception.OrderNotFoundException;
import com.publicnext.orders.repository.InventoryRepository;
import com.publicnext.orders.repository.OrderEventRepository;
import com.publicnext.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private OrderEventRepository orderEventRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_savesOrderWithLinesAndComputedTotal_andRecordsCreatedEvent() {
        UUID customerId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(
                        new CreateOrderRequest.LineRequest(p1, 3, new BigDecimal("10.00")),
                        new CreateOrderRequest.LineRequest(p2, 2, new BigDecimal("7.50"))
                )
        );

        when(inventoryRepository.findAllById(any()))
                .thenReturn(List.of(inventory(p1, 100), inventory(p2, 100)));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            o.setUpdatedAt(Instant.now());
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("45.00");
        assertThat(saved.getOrderLines()).hasSize(2);
        assertThat(saved.getOrderLines())
                .extracting(OrderLine::getProductId)
                .containsExactly(p1, p2);

        assertThat(response.totalAmount()).isEqualByComparingTo("45.00");

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(eventCaptor.capture());
        OrderEvent recorded = eventCaptor.getValue();
        assertThat(recorded.getEventType()).isEqualTo(OrderEventType.ORDER_CREATED);
        assertThat(recorded.getFromStatus()).isNull();
        assertThat(recorded.getToStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(recorded.getOrder()).isSameAs(saved);
    }

    @Test
    void createOrder_insufficientStock_throwsAndDoesNotSave() {
        UUID p1 = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(p1, 100, new BigDecimal("10.00")))
        );

        when(inventoryRepository.findAllById(any())).thenReturn(List.of(inventory(p1, 5)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.getDetails()).hasSize(1);
                    assertThat(ex.getDetails().get(0).requested()).isEqualTo(100);
                    assertThat(ex.getDetails().get(0).available()).isEqualTo(5);
                });

        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void createOrder_unknownProduct_throwsAsZeroAvailable() {
        UUID unknown = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.LineRequest(unknown, 1, new BigDecimal("5.00")))
        );

        when(inventoryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.getDetails().get(0).available()).isZero();
                });
    }

    @Test
    void createOrder_sumsQuantitiesPerProductBeforeChecking() {
        UUID p = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(
                        new CreateOrderRequest.LineRequest(p, 3, new BigDecimal("10.00")),
                        new CreateOrderRequest.LineRequest(p, 3, new BigDecimal("10.00"))
                )
        );

        when(inventoryRepository.findAllById(any())).thenReturn(List.of(inventory(p, 5)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.getDetails().get(0).requested()).isEqualTo(6);
                });
    }

    @Test
    void transitionStatus_validTransition_updatesStatusAndRecordsEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithStatus(orderId, OrderStatus.UNPROCESSED);

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.transitionStatus(orderId, OrderStatus.PROCESSING);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        OrderEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(OrderEventType.STATUS_CHANGED);
        assertThat(event.getFromStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(event.getToStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void transitionStatus_skippingState_throwsAndPersistsNothing() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithStatus(orderId, OrderStatus.UNPROCESSED);

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(orderId, OrderStatus.PROCESSED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void transitionStatus_fromTerminalState_throws() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithStatus(orderId, OrderStatus.SHIPPED);

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(orderId, OrderStatus.PROCESSED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void transitionStatus_unknownOrder_throws404() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.transitionStatus(orderId, OrderStatus.PROCESSING))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void getByOrderId_returnsMappedResponse_whenPresent() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PROCESSING)
                .orderDate(Instant.now())
                .totalAmount(new BigDecimal("12.34"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getByOrderId(orderId);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void getByOrderId_throwsWhenAbsent() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getByOrderId(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void getHistory_returnsMappedEvents_whenOrderExists() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsByOrderId(orderId)).thenReturn(true);

        OrderEvent created = new OrderEvent();
        created.setEventType(OrderEventType.ORDER_CREATED);
        created.setToStatus(OrderStatus.UNPROCESSED);
        created.setOccurredAt(Instant.parse("2026-01-01T10:00:00Z"));

        OrderEvent transitioned = new OrderEvent();
        transitioned.setEventType(OrderEventType.STATUS_CHANGED);
        transitioned.setFromStatus(OrderStatus.UNPROCESSED);
        transitioned.setToStatus(OrderStatus.PROCESSING);
        transitioned.setOccurredAt(Instant.parse("2026-01-01T10:05:00Z"));

        when(orderEventRepository.findByOrder_OrderIdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of(created, transitioned));

        List<OrderEventResponse> result = orderService.getHistory(orderId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrderEventResponse::eventType)
                .containsExactly(OrderEventType.ORDER_CREATED, OrderEventType.STATUS_CHANGED);
    }

    @Test
    void getHistory_unknownOrder_throws404() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsByOrderId(orderId)).thenReturn(false);

        assertThatThrownBy(() -> orderService.getHistory(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderEventRepository, never()).findByOrder_OrderIdOrderByOccurredAtAsc(any());
    }

    private Inventory inventory(UUID productId, int stock) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setAvailableStock(stock);
        return inv;
    }

    private Order orderWithStatus(UUID orderId, OrderStatus status) {
        return Order.builder()
                .orderId(orderId)
                .customerId(UUID.randomUUID())
                .status(status)
                .orderDate(Instant.now())
                .totalAmount(new BigDecimal("10.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
