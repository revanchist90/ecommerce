package com.publicnext.notifications.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.notifications.dispatch.NotificationDispatcher;
import com.publicnext.notifications.dispatch.NotificationRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(dispatcher, new ObjectMapper());
    }

    @Test
    void orderCreated_dispatchesWithCustomerRecipient() {
        ConsumerRecord<String, String> record = recordWithEventType("ORDER_CREATED",
                """
                {"orderId":"o-1","customerId":"c-1","status":"UNPROCESSED","totalAmount":10.00}
                """);

        listener.onMessage(record);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest req = captor.getValue();
        assertThat(req.orderId()).isEqualTo("o-1");
        assertThat(req.recipient()).isEqualTo("customer:c-1");
        assertThat(req.subject()).isEqualTo("Order received");
        assertThat(req.message()).contains("o-1").contains("received");
    }

    @Test
    void statusChangedToShipped_dispatches() {
        ConsumerRecord<String, String> record = recordWithEventType("STATUS_CHANGED",
                """
                {"orderId":"o-2","fromStatus":"PROCESSED","toStatus":"SHIPPED"}
                """);

        listener.onMessage(record);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest req = captor.getValue();
        assertThat(req.recipient()).isEqualTo("order:o-2");
        assertThat(req.message()).contains("SHIPPED");
    }

    @Test
    void statusChangedToProcessing_isSkipped() {
        ConsumerRecord<String, String> record = recordWithEventType("STATUS_CHANGED",
                """
                {"orderId":"o-3","fromStatus":"UNPROCESSED","toStatus":"PROCESSING"}
                """);

        listener.onMessage(record);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orderDeleted_dispatches() {
        ConsumerRecord<String, String> record = recordWithEventType("ORDER_DELETED",
                """
                {"orderId":"o-4","statusAtDeletion":"UNPROCESSED"}
                """);

        listener.onMessage(record);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Order cancelled");
    }

    @Test
    void orderUpdated_isIgnored() {
        ConsumerRecord<String, String> record = recordWithEventType("ORDER_UPDATED",
                """
                {"orderId":"o-5","customerId":"c-5"}
                """);

        listener.onMessage(record);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingEventTypeHeader_isSkipped() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders.events", 0, 0L, "k",
                """
                {"orderId":"o-6"}
                """);

        listener.onMessage(record);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedPayload_isSwallowed_andDoesNotDispatch() {
        ConsumerRecord<String, String> record = recordWithEventType("ORDER_CREATED", "{not json");

        listener.onMessage(record);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    private ConsumerRecord<String, String> recordWithEventType(String eventType, String json) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "orders.events", 0, 0L, "key", json);
        record.headers().add(OrderEventListener.HEADER_EVENT_TYPE,
                eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
