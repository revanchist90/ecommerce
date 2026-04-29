package com.publicnext.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orders.auto-progression")
public record OrderProgressionProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration unprocessedToProcessing,
        Duration processingToProcessed,
        Duration processedToShipped
) {
}
