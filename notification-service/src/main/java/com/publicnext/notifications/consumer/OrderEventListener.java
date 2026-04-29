package com.publicnext.notifications.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.notifications.dispatch.NotificationDeliveryException;
import com.publicnext.notifications.dispatch.NotificationDispatcher;
import com.publicnext.notifications.dispatch.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    public static final String HEADER_EVENT_TYPE = "event_type";

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "${notifications.retry.attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${notifications.retry.delay-ms:5000}",
                    multiplierExpression = "${notifications.retry.multiplier:6}"
            ),
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = "${orders.kafka.topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, HEADER_EVENT_TYPE);
        if (eventType == null) {
            log.warn("Skipping record at offset {} partition {} — missing event_type header",
                    record.offset(), record.partition());
            return;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(record.value());
        } catch (Exception parseFailure) {
            // Malformed JSON is a permanent error; don't trigger retries.
            log.error("Skipping record offset={} partition={} eventType={} — malformed JSON: {}",
                    record.offset(), record.partition(), eventType, parseFailure.getMessage());
            return;
        }

        NotificationRequest request = buildRequest(eventType, payload);
        if (request == null) {
            return;
        }

        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);
        if (outcome == NotificationDispatcher.DispatchOutcome.FAILED) {
            // Both channels failed — let retry topology take over.
            throw new NotificationDeliveryException(
                    "Notification dispatch failed for order " + request.orderId());
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, HEADER_EVENT_TYPE);
        log.error("[DLT] Notification permanently failed: topic={} key={} eventType={} value={}",
                record.topic(), record.key(), eventType, record.value());
    }

    private NotificationRequest buildRequest(String eventType, JsonNode payload) {
        String orderId = textOrNull(payload, "orderId");
        if (orderId == null) {
            return null;
        }
        String customerId = textOrNull(payload, "customerId");
        String recipient = customerId != null ? "customer:" + customerId : "order:" + orderId;

        return switch (eventType) {
            case "ORDER_CREATED" -> new NotificationRequest(
                    orderId, recipient,
                    "Order received",
                    "Thank you — order " + orderId + " has been received.");
            case "STATUS_CHANGED" -> {
                String to = textOrNull(payload, "toStatus");
                if (!"SHIPPED".equals(to) && !"PROCESSED".equals(to)) {
                    yield null;
                }
                yield new NotificationRequest(
                        orderId, recipient,
                        "Order update",
                        "Order " + orderId + " is now " + to + ".");
            }
            case "ORDER_DELETED" -> new NotificationRequest(
                    orderId, recipient,
                    "Order cancelled",
                    "Order " + orderId + " has been cancelled.");
            default -> null;
        };
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
