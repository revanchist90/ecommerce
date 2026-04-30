package com.publicnext.analytics.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.analytics.domain.ProcessedEvent;
import com.publicnext.analytics.repository.ProcessedEventRepository;
import com.publicnext.analytics.service.CustomerStatsUpdater;
import com.publicnext.analytics.service.MetricsUpdater;
import com.publicnext.analytics.service.StatusDistributionUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    public static final String HEADER_EVENT_TYPE = "event_type";
    public static final String HEADER_OUTBOX_ID = "outbox_id";

    private final MetricsUpdater dailyUpdater;
    private final StatusDistributionUpdater statusUpdater;
    private final CustomerStatsUpdater customerUpdater;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${orders.kafka.topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String outboxId = headerValue(record, HEADER_OUTBOX_ID);
        String eventType = headerValue(record, HEADER_EVENT_TYPE);

        if (outboxId == null || eventType == null) {
            log.warn("Skipping record offset={} partition={} — missing outbox_id or event_type",
                    record.offset(), record.partition());
            return;
        }

        // Claim pattern: try to insert dedup row first. If it already exists, skip.
        try {
            processedEvents.insert(new ProcessedEvent(outboxId, eventType, Instant.now()));
        } catch (DuplicateKeyException duplicate) {
            log.debug("Already processed outbox_id={}, skipping", outboxId);
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            apply(eventType, payload);
        } catch (Exception applyFailure) {
            // The dedup marker is intentionally not rolled back — the demo accepts a lost
            // metric over a redelivery loop. For at-most-once with a transaction, wrap
            // the insert + update in a Mongo session.
            log.error("Failed to apply analytics update for outbox_id={} eventType={}: {}",
                    outboxId, eventType, applyFailure.getMessage(), applyFailure);
        }
    }

    private void apply(String eventType, JsonNode payload) {
        switch (eventType) {
            case "ORDER_CREATED" -> {
                LocalDate day = parseDay(payload, "orderDate");
                BigDecimal totalAmount = readDecimal(payload, "totalAmount");
                String customerId = textOrNull(payload, "customerId");
                if (day != null && totalAmount != null) {
                    dailyUpdater.onOrderCreated(day, totalAmount);
                }
                statusUpdater.onOrderCreated();
                customerUpdater.onOrderCreated(customerId, totalAmount);
            }
            case "STATUS_CHANGED" -> {
                String from = textOrNull(payload, "fromStatus");
                String to = textOrNull(payload, "toStatus");
                statusUpdater.onStatusChanged(from, to);
                if ("SHIPPED".equals(to)) {
                    LocalDate day = parseDay(payload, "occurredAt");
                    if (day != null) {
                        dailyUpdater.onOrderShipped(day);
                    }
                }
            }
            case "ORDER_DELETED" -> {
                LocalDate day = parseDay(payload, "deletedAt");
                if (day != null) {
                    dailyUpdater.onOrderCancelled(day);
                }
                statusUpdater.onOrderDeleted(textOrNull(payload, "statusAtDeletion"));
            }
            default -> {
                // ORDER_UPDATED and unknown types don't move analytics counters
            }
        }
    }

    private LocalDate parseDay(JsonNode payload, String field) {
        String iso = textOrNull(payload, field);
        if (iso == null) {
            return null;
        }
        try {
            return Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            log.warn("Could not parse {} as Instant: {}", field, iso);
            return null;
        }
    }

    private BigDecimal readDecimal(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.decimalValue();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
