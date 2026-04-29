package com.publicnext.orders.service;

import com.publicnext.orders.domain.Inventory;
import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderEvent;
import com.publicnext.orders.domain.OrderEventType;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.dto.OrderEventResponse;
import com.publicnext.orders.dto.OrderLineInput;
import com.publicnext.orders.dto.OrderListFilter;
import com.publicnext.orders.dto.OrderResponse;
import com.publicnext.orders.dto.PagedResponse;
import com.publicnext.orders.dto.UpdateOrderRequest;
import com.publicnext.orders.dto.event.OrderCreatedPayload;
import com.publicnext.orders.dto.event.OrderDeletedPayload;
import com.publicnext.orders.dto.event.OrderStatusChangedPayload;
import com.publicnext.orders.dto.event.OrderUpdatedPayload;
import com.publicnext.orders.exception.InsufficientStockException;
import com.publicnext.orders.exception.InvalidStatusTransitionException;
import com.publicnext.orders.exception.OrderNotEditableException;
import com.publicnext.orders.exception.OrderNotFoundException;
import com.publicnext.orders.repository.InventoryRepository;
import com.publicnext.orders.repository.OrderEventRepository;
import com.publicnext.orders.repository.OrderRepository;
import com.publicnext.orders.repository.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = Map.of(
            OrderStatus.UNPROCESSED, OrderStatus.PROCESSING,
            OrderStatus.PROCESSING,  OrderStatus.PROCESSED,
            OrderStatus.PROCESSED,   OrderStatus.SHIPPED
    );

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderEventRepository orderEventRepository;
    private final OutboxRecorder outboxRecorder;

    public OrderResponse createOrder(CreateOrderRequest request) {
        validateInventory(request.orderLines());

        Order order = Order.builder()
                .customerId(request.customerId())
                .status(OrderStatus.UNPROCESSED)
                .orderDate(Instant.now())
                .totalAmount(computeTotal(request.orderLines()))
                .build();

        request.orderLines().forEach(line -> order.addOrderLine(toOrderLine(line)));

        Order saved = orderRepository.save(order);
        recordEvent(saved, OrderEventType.ORDER_CREATED, null, saved.getStatus());
        outboxRecorder.record(
                OutboxRecorder.AGGREGATE_ORDER,
                saved.getOrderId(),
                OrderEventType.ORDER_CREATED.name(),
                OrderCreatedPayload.from(saved));
        return OrderResponse.from(saved);
    }

    public OrderResponse updateOrder(UUID orderId, UpdateOrderRequest request) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.UNPROCESSED) {
            throw new OrderNotEditableException(orderId, order.getStatus());
        }

        validateInventory(request.orderLines());

        order.setCustomerId(request.customerId());
        order.getOrderLines().clear();
        request.orderLines().forEach(line -> order.addOrderLine(toOrderLine(line)));
        order.setTotalAmount(computeTotal(request.orderLines()));

        Order saved = orderRepository.save(order);
        recordEvent(saved, OrderEventType.ORDER_UPDATED, null, null);
        outboxRecorder.record(
                OutboxRecorder.AGGREGATE_ORDER,
                saved.getOrderId(),
                OrderEventType.ORDER_UPDATED.name(),
                OrderUpdatedPayload.from(saved));
        return OrderResponse.from(saved);
    }

    public OrderResponse transitionStatus(UUID orderId, OrderStatus target) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus current = order.getStatus();
        if (NEXT_STATUS.get(current) != target) {
            throw new InvalidStatusTransitionException(orderId, current, target);
        }

        order.setStatus(target);
        Order saved = orderRepository.save(order);
        recordEvent(saved, OrderEventType.STATUS_CHANGED, current, target);
        outboxRecorder.record(
                OutboxRecorder.AGGREGATE_ORDER,
                saved.getOrderId(),
                OrderEventType.STATUS_CHANGED.name(),
                new OrderStatusChangedPayload(saved.getOrderId(), current, target, Instant.now()));
        return OrderResponse.from(saved);
    }

    public void softDelete(UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Instant deletedAt = Instant.now();
        OrderStatus statusAtDeletion = order.getStatus();
        order.setDeletedAt(deletedAt);
        Order saved = orderRepository.save(order);

        recordEvent(saved, OrderEventType.ORDER_DELETED, statusAtDeletion, null);
        outboxRecorder.record(
                OutboxRecorder.AGGREGATE_ORDER,
                saved.getOrderId(),
                OrderEventType.ORDER_DELETED.name(),
                new OrderDeletedPayload(saved.getOrderId(), statusAtDeletion, deletedAt));
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listOrders(OrderListFilter filter, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(OrderSpecifications.matching(filter), pageable);
        return PagedResponse.of(page, OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getByOrderId(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getHistory(UUID orderId) {
        if (!orderRepository.existsByOrderId(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return orderEventRepository.findByOrder_OrderIdOrderByOccurredAtAsc(orderId).stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    private void recordEvent(Order order, OrderEventType type, OrderStatus from, OrderStatus to) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(type);
        event.setFromStatus(from);
        event.setToStatus(to);
        orderEventRepository.save(event);
    }

    private void validateInventory(List<? extends OrderLineInput> lines) {
        Map<UUID, Integer> requestedByProduct = lines.stream()
                .collect(Collectors.groupingBy(
                        OrderLineInput::productId,
                        Collectors.summingInt(OrderLineInput::quantity)
                ));

        Set<UUID> productIds = requestedByProduct.keySet();
        Map<UUID, Integer> availableByProduct = inventoryRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getAvailableStock));

        List<InsufficientStockException.Detail> issues = requestedByProduct.entrySet().stream()
                .filter(e -> availableByProduct.getOrDefault(e.getKey(), 0) < e.getValue())
                .map(e -> new InsufficientStockException.Detail(
                        e.getKey(),
                        e.getValue(),
                        availableByProduct.getOrDefault(e.getKey(), 0)
                ))
                .toList();

        if (!issues.isEmpty()) {
            throw new InsufficientStockException(issues);
        }
    }

    private OrderLine toOrderLine(OrderLineInput line) {
        return OrderLine.builder()
                .productId(line.productId())
                .quantity(line.quantity())
                .unitPrice(line.unitPrice())
                .build();
    }

    private BigDecimal computeTotal(List<? extends OrderLineInput> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
