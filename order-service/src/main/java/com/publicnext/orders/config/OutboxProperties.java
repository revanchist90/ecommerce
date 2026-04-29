package com.publicnext.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.poll")
public record OutboxProperties(
        boolean enabled,
        Duration fixedDelay,
        int batchSize
) {
}
