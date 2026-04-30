package com.publicnext.analytics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.analytics.domain.ProcessedEvent;
import com.publicnext.analytics.repository.ProcessedEventRepository;
import com.publicnext.analytics.service.CustomerStatsUpdater;
import com.publicnext.analytics.service.MetricsUpdater;
import com.publicnext.analytics.service.StatusDistributionUpdater;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private MetricsUpdater updater;

    @Mock
    private StatusDistributionUpdater statusUpdater;

    @Mock
    private CustomerStatsUpdater customerUpdater;

    @Mock
    private ProcessedEventRepository processedEvents;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(updater, statusUpdater, customerUpdater,
                processedEvents, new ObjectMapper());
    }

    @Test
    void orderCreated_recordsDedup_andDispatchesToAllThreeUpdaters() {
        ConsumerRecord<String, String> record = recordOf("ORDER_CREATED", "ob-1",
                """
                {"orderId":"o-1","customerId":"c-1","totalAmount":12.50,"orderDate":"2026-04-29T10:00:00Z"}
                """);

        consumer.onMessage(record);

        verify(processedEvents).insert(any(ProcessedEvent.class));
        verify(updater).onOrderCreated(eq(LocalDate.of(2026, 4, 29)),
                argThat(bd -> bd.compareTo(new BigDecimal("12.50")) == 0));
        verify(statusUpdater).onOrderCreated();
        verify(customerUpdater).onOrderCreated(eq("c-1"),
                argThat(bd -> bd.compareTo(new BigDecimal("12.50")) == 0));
    }

    @Test
    void duplicateOutboxId_skipsAllUpdaters() {
        when(processedEvents.insert(any(ProcessedEvent.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        ConsumerRecord<String, String> record = recordOf("ORDER_CREATED", "ob-dup",
                """
                {"orderId":"o-dup","totalAmount":1.00,"orderDate":"2026-04-29T10:00:00Z"}
                """);

        consumer.onMessage(record);

        verifyNoInteractions(updater);
        verifyNoInteractions(statusUpdater);
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void statusChangedToShipped_movesStatusCount_andBumpsShippedCounter() {
        ConsumerRecord<String, String> record = recordOf("STATUS_CHANGED", "ob-2",
                """
                {"orderId":"o-2","fromStatus":"PROCESSED","toStatus":"SHIPPED","occurredAt":"2026-04-29T11:00:00Z"}
                """);

        consumer.onMessage(record);

        verify(updater).onOrderShipped(LocalDate.of(2026, 4, 29));
        verify(statusUpdater).onStatusChanged("PROCESSED", "SHIPPED");
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void statusChangedToProcessing_movesStatusCount_butSkipsDailyUpdater() {
        ConsumerRecord<String, String> record = recordOf("STATUS_CHANGED", "ob-3",
                """
                {"orderId":"o-3","fromStatus":"UNPROCESSED","toStatus":"PROCESSING","occurredAt":"2026-04-29T11:00:00Z"}
                """);

        consumer.onMessage(record);

        verify(updater, never()).onOrderShipped(any());
        verify(updater, never()).onOrderCancelled(any());
        verify(statusUpdater).onStatusChanged("UNPROCESSED", "PROCESSING");
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void orderDeleted_decrementsStatus_andBumpsCancelledCounter() {
        ConsumerRecord<String, String> record = recordOf("ORDER_DELETED", "ob-4",
                """
                {"orderId":"o-4","statusAtDeletion":"UNPROCESSED","deletedAt":"2026-04-29T12:00:00Z"}
                """);

        consumer.onMessage(record);

        verify(updater).onOrderCancelled(LocalDate.of(2026, 4, 29));
        verify(statusUpdater).onOrderDeleted("UNPROCESSED");
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void orderUpdated_doesNotMoveAnyCounter() {
        ConsumerRecord<String, String> record = recordOf("ORDER_UPDATED", "ob-5",
                """
                {"orderId":"o-5","totalAmount":99.99}
                """);

        consumer.onMessage(record);

        verify(processedEvents).insert(any(ProcessedEvent.class));
        verifyNoInteractions(updater);
        verifyNoInteractions(statusUpdater);
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void missingOutboxIdHeader_skipsBeforeDedup() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders.events", 0, 0L, "k", "{}");
        record.headers().add(OrderEventConsumer.HEADER_EVENT_TYPE,
                "ORDER_CREATED".getBytes(StandardCharsets.UTF_8));

        consumer.onMessage(record);

        verifyNoInteractions(processedEvents);
        verifyNoInteractions(updater);
        verifyNoInteractions(statusUpdater);
        verifyNoInteractions(customerUpdater);
    }

    @Test
    void malformedPayload_keepsDedupRow_butDoesNotCallUpdaters() {
        ConsumerRecord<String, String> record = recordOf("ORDER_CREATED", "ob-bad", "{not json");

        consumer.onMessage(record);

        verify(processedEvents).insert(any(ProcessedEvent.class));
        verifyNoInteractions(updater);
        verifyNoInteractions(statusUpdater);
        verifyNoInteractions(customerUpdater);
    }

    private ConsumerRecord<String, String> recordOf(String eventType, String outboxId, String json) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "orders.events", 0, 0L, "key", json);
        record.headers().add(OrderEventConsumer.HEADER_EVENT_TYPE,
                eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add(OrderEventConsumer.HEADER_OUTBOX_ID,
                outboxId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
