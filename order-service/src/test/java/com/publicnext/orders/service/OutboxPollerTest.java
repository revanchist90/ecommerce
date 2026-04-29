package com.publicnext.orders.service;

import com.publicnext.orders.config.OutboxProperties;
import com.publicnext.orders.domain.OutboxEvent;
import com.publicnext.orders.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxPublisher publisher;

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(true, Duration.ofSeconds(2), 50);
        poller = new OutboxPoller(outboxRepository, publisher, properties);
    }

    @Test
    void poll_publishesAndMarksEachEventAsPublished() {
        OutboxEvent e1 = newEvent();
        OutboxEvent e2 = newEvent();
        when(outboxRepository.lockUnpublishedBatch(50)).thenReturn(List.of(e1, e2));

        poller.poll();

        verify(publisher).publish(e1);
        verify(publisher).publish(e2);
        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e2.getPublishedAt()).isNotNull();
        assertThat(e1.getAttempts()).isZero();
        assertThat(e2.getAttempts()).isZero();
    }

    @Test
    void poll_recordsFailureWithoutMarkingPublished_andContinuesWithRest() {
        OutboxEvent failing = newEvent();
        OutboxEvent succeeding = newEvent();
        when(outboxRepository.lockUnpublishedBatch(50)).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("kafka down")).when(publisher).publish(failing);

        poller.poll();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).contains("kafka down");

        assertThat(succeeding.getPublishedAt()).isNotNull();
        verify(publisher).publish(succeeding);
    }

    @Test
    void poll_doesNothingWhenBatchIsEmpty() {
        when(outboxRepository.lockUnpublishedBatch(50)).thenReturn(List.of());

        poller.poll();

        verifyNoInteractions(publisher);
    }

    @Test
    void poll_usesConfiguredBatchSize() {
        OutboxProperties customProperties = new OutboxProperties(true, Duration.ofSeconds(2), 7);
        OutboxPoller customPoller = new OutboxPoller(outboxRepository, publisher, customProperties);
        when(outboxRepository.lockUnpublishedBatch(any(Integer.class))).thenReturn(List.of());

        customPoller.poll();

        verify(outboxRepository).lockUnpublishedBatch(7);
    }

    private OutboxEvent newEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("ORDER_CREATED");
        event.setPayload("{}");
        return event;
    }
}
