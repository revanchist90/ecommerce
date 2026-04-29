package com.publicnext.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orders.kafka")
public record KafkaTopicProperties(
        String topic,
        Duration sendTimeout
) {
}
