package com.publicnext.notifications.dispatch;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=PLAINTEXT://localhost:9092"
})
class CircuitBreakerOpenFallbackTest {

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @SpyBean
    private SmsNotificationGateway smsGateway;

    @AfterEach
    void resetCircuit() {
        circuitBreakerRegistry.circuitBreaker(EmailNotificationGateway.CIRCUIT_BREAKER_NAME).reset();
    }

    @Test
    void emailCircuitOpen_dispatcherFallsBackToSms() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(EmailNotificationGateway.CIRCUIT_BREAKER_NAME);
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        NotificationRequest request = new NotificationRequest(
                "order-cb", "customer:cb", "subject", "message");

        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);

        assertThat(outcome).isEqualTo(NotificationDispatcher.DispatchOutcome.SMS);
        verify(smsGateway).send(request);
    }

    @Test
    void emailCircuitClosed_dispatcherUsesEmail_andSmsNotInvoked() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(EmailNotificationGateway.CIRCUIT_BREAKER_NAME);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        NotificationRequest request = new NotificationRequest(
                "order-ok", "customer:ok", "subject", "message");

        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);

        assertThat(outcome).isEqualTo(NotificationDispatcher.DispatchOutcome.EMAIL);
        verifyNoInteractions(smsGateway);
    }
}
