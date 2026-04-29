package com.publicnext.orders.repository;

import com.publicnext.orders.domain.OutboxEvent;
import com.publicnext.orders.persistence.BaseDataJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRepositoryTest extends BaseDataJpaTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void lockUnpublishedBatch_returnsOnlyUnpublished_inCreatedAtOrder_uptoLimit() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID a3 = UUID.randomUUID();

        OutboxEvent first  = save(a1, "ORDER_CREATED",  Instant.parse("2026-01-01T10:00:00Z"), null);
        OutboxEvent second = save(a2, "STATUS_CHANGED", Instant.parse("2026-01-01T10:01:00Z"), null);
        save(a3, "STATUS_CHANGED", Instant.parse("2026-01-01T10:02:00Z"),
                Instant.parse("2026-01-01T10:03:00Z"));
        save(a1, "STATUS_CHANGED", Instant.parse("2026-01-01T10:04:00Z"), null);

        List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(2);

        assertThat(batch).hasSize(2);
        assertThat(batch).extracting(OutboxEvent::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void lockUnpublishedBatch_excludesPublishedRows() {
        OutboxEvent published = save(UUID.randomUUID(), "ORDER_CREATED",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:01:00Z"));
        OutboxEvent unpublished = save(UUID.randomUUID(), "STATUS_CHANGED",
                Instant.parse("2026-01-01T10:02:00Z"), null);

        List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(100);

        assertThat(batch).extracting(OutboxEvent::getId)
                .contains(unpublished.getId())
                .doesNotContain(published.getId());
    }

    @Test
    void findByAggregateId_returnsRowsForThatAggregate_inOrder() {
        UUID aggregate = UUID.randomUUID();
        save(aggregate, "ORDER_CREATED",  Instant.parse("2026-01-01T10:00:00Z"), null);
        save(aggregate, "STATUS_CHANGED", Instant.parse("2026-01-01T10:01:00Z"), null);
        save(UUID.randomUUID(), "STATUS_CHANGED", Instant.parse("2026-01-01T10:02:00Z"), null);

        List<OutboxEvent> rows = outboxRepository.findByAggregateIdOrderByCreatedAtAsc(aggregate);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OutboxEvent::getEventType)
                .containsExactly("ORDER_CREATED", "STATUS_CHANGED");
    }

    private OutboxEvent save(UUID aggregateId, String eventType, Instant createdAt, Instant publishedAt) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload("{}");
        event.setCreatedAt(createdAt);
        event.setPublishedAt(publishedAt);
        return outboxRepository.save(event);
    }
}
