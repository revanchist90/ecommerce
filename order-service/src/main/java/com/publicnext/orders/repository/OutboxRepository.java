package com.publicnext.orders.repository;

import com.publicnext.orders.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT *
              FROM outbox
             WHERE published_at IS NULL
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(UUID aggregateId);
}
