package com.publicnext.orders.service;

import com.publicnext.orders.domain.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(OutboxPublisher.class)
public class LoggingOutboxPublisher implements OutboxPublisher {

    @Override
    public void publish(OutboxEvent event) {
        log.info("[outbox] publishing id={} aggregate={}/{} type={} payloadBytes={}",
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload() == null ? 0 : event.getPayload().length());
    }
}
