package com.publicnext.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.orders.config.KafkaTopicProperties;
import com.publicnext.orders.domain.OutboxEvent;
import com.publicnext.orders.dto.CreateOrderRequest;
import com.publicnext.orders.repository.OutboxRepository;
import com.publicnext.orders.service.KafkaOutboxPublisher;
import com.publicnext.orders.service.OutboxPublisher;
import com.publicnext.orders.support.BaseIntegrationTest;
import com.publicnext.orders.support.TestKafkaContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderKafkaPublishingTest extends BaseIntegrationTest {

    private static final UUID PRODUCT_100 = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaTopicProperties kafkaTopicProperties;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void createOrder_publishedThroughOutbox_lands_onOrdersEventsTopic() throws Exception {
        UUID customerId = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderRequest.LineRequest(PRODUCT_100, 2, new BigDecimal("10.00")))
        );

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID orderId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("orderId").asText());

        // Drive publishing manually (same semantics as OutboxPoller, just without scheduled trigger)
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            List<OutboxEvent> batch = outboxRepository.lockUnpublishedBatch(50);
            for (OutboxEvent event : batch) {
                outboxPublisher.publish(event);
                event.markPublished();
            }
        });

        ConsumerRecord<String, String> record = consumeOne(kafkaTopicProperties.topic(), orderId);

        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(orderId.toString());
        assertThat(headerValue(record, KafkaOutboxPublisher.HEADER_EVENT_TYPE)).isEqualTo("ORDER_CREATED");
        assertThat(headerValue(record, KafkaOutboxPublisher.HEADER_AGGREGATE_TYPE)).isEqualTo("ORDER");
        assertThat(headerValue(record, KafkaOutboxPublisher.HEADER_OUTBOX_ID)).isNotBlank();

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(payload.get("status").asText()).isEqualTo("UNPROCESSED");
        assertThat(payload.get("totalAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("20.00"));

        // Outbox row is now marked published
        List<OutboxEvent> rows = outboxRepository.findByAggregateIdOrderByCreatedAtAsc(orderId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPublishedAt()).isNotNull();
    }

    private ConsumerRecord<String, String> consumeOne(String topic, UUID expectedKey) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafkaContainer.INSTANCE.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
            while (System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    if (expectedKey.toString().equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    private String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
