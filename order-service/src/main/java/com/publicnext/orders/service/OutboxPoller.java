package com.publicnext.orders.service;

import com.publicnext.orders.config.OutboxProperties;
import com.publicnext.orders.domain.OutboxEvent;
import com.publicnext.orders.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.poll", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${outbox.poll.fixed-delay}")
    @Transactional
    public void poll() {
        List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Polling outbox: locked {} unpublished event(s)", batch.size());
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(event);
                event.markPublished();
            } catch (Exception e) {
                event.recordFailure(e.getMessage());
                log.warn("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
