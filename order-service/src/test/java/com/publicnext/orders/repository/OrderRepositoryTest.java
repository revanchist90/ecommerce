package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Order;
import com.publicnext.orders.domain.OrderLine;
import com.publicnext.orders.domain.OrderStatus;
import com.publicnext.orders.persistence.BaseDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRepositoryTest extends BaseDataJpaTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void save_persistsOrderWithLinesAndComputesLineTotal() {
        Order order = newOrder();
        order.addOrderLine(line(UUID.randomUUID(), 3, "10.5000"));
        order.addOrderLine(line(UUID.randomUUID(), 2, "7.2500"));

        Order saved = orderRepository.saveAndFlush(order);
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getOrderId()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getOrderLines()).hasSize(2);
        assertThat(reloaded.getOrderLines())
                .extracting(OrderLine::getLineTotal)
                .containsExactlyInAnyOrder(new BigDecimal("31.5000"), new BigDecimal("14.5000"));
    }

    @Test
    void findByOrderId_returnsOrderWhenPresent() {
        Order saved = orderRepository.saveAndFlush(newOrder());
        em.clear();

        Optional<Order> found = orderRepository.findByOrderId(saved.getOrderId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByOrderId_returnsEmptyWhenAbsent() {
        Optional<Order> found = orderRepository.findByOrderId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void delete_cascadesToOrderLines() {
        Order order = newOrder();
        order.addOrderLine(line(UUID.randomUUID(), 1, "5.0000"));
        Order saved = orderRepository.saveAndFlush(order);
        Long lineId = saved.getOrderLines().get(0).getId();
        em.clear();

        orderRepository.deleteById(saved.getId());
        em.flush();
        em.clear();

        assertThat(em.find(Order.class, saved.getId())).isNull();
        assertThat(em.find(OrderLine.class, lineId)).isNull();
    }

    @Test
    void update_incrementsVersion() {
        Order saved = orderRepository.saveAndFlush(newOrder());
        Long initialVersion = saved.getVersion();
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        reloaded.setStatus(OrderStatus.PROCESSING);
        orderRepository.saveAndFlush(reloaded);

        assertThat(reloaded.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void concurrentUpdate_throwsOptimisticLockingFailure() {
        Order saved = orderRepository.saveAndFlush(newOrder());
        em.clear();

        Order first = orderRepository.findById(saved.getId()).orElseThrow();
        Order second = em.getEntityManager()
                .createQuery("SELECT o FROM Order o WHERE o.id = :id", Order.class)
                .setParameter("id", saved.getId())
                .setHint("jakarta.persistence.cache.storeMode", "REFRESH")
                .getSingleResult();
        em.detach(second);

        first.setStatus(OrderStatus.PROCESSING);
        orderRepository.saveAndFlush(first);

        second.setStatus(OrderStatus.PROCESSED);
        assertThatThrownBy(() -> orderRepository.saveAndFlush(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void softDelete_persistsDeletedAt() {
        Order saved = orderRepository.saveAndFlush(newOrder());
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        Instant deletedAt = Instant.now();
        reloaded.setDeletedAt(deletedAt);
        orderRepository.saveAndFlush(reloaded);
        em.clear();

        Order afterDelete = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterDelete.getDeletedAt()).isNotNull();
    }

    private Order newOrder() {
        return Order.builder()
                .customerId(UUID.randomUUID())
                .status(OrderStatus.UNPROCESSED)
                .orderDate(Instant.now())
                .totalAmount(new BigDecimal("0.0000"))
                .build();
    }

    private OrderLine line(UUID productId, int quantity, String unitPrice) {
        return OrderLine.builder()
                .productId(productId)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }
}
