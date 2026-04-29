package com.publicnext.orders.service;

import com.publicnext.orders.config.KafkaTopicProperties;
import com.publicnext.orders.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox", name = "publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaOutboxPublisher implements OutboxPublisher {

    public static final String HEADER_EVENT_TYPE = "event_type";
    public static final String HEADER_AGGREGATE_TYPE = "aggregate_type";
    public static final String HEADER_OUTBOX_ID = "outbox_id";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties properties;

    @Override
    public void publish(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topic(),
                null,
                event.getAggregateId().toString(),
                event.getPayload()
        );
        record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HEADER_AGGREGATE_TYPE, event.getAggregateType().getBytes(StandardCharsets.UTF_8));
        if (event.getId() != null) {
            record.headers().add(HEADER_OUTBOX_ID, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + event.getId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish outbox event " + event.getId() + " to Kafka", e);
        }
    }
}
