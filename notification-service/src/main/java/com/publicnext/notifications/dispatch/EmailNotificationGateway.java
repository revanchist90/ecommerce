package com.publicnext.notifications.dispatch;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationGateway {

    public static final String CIRCUIT_BREAKER_NAME = "email";

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public void send(NotificationRequest request) {
        log.info("[email] to={} subject=\"{}\" body=\"{}\" orderId={}",
                request.recipient(), request.subject(), request.message(), request.orderId());
    }
}
