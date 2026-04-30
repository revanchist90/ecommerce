package com.publicnext.analytics.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicnext.analytics.consumer.OrderEventConsumer;
import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.domain.ProcessedEvent;
import com.publicnext.analytics.domain.StatusCount;
import com.publicnext.analytics.support.TestKafkaContainer;
import com.publicnext.analytics.support.TestMongoContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsEndToEndTest {

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", TestKafkaContainer.INSTANCE::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", TestMongoContainer.INSTANCE::getReplicaSetUrl);
        // Fresh consumer group per JVM run so we always replay from earliest in this topic
        registry.add("spring.kafka.consumer.group-id", () -> "analytics-e2e-" + UUID.randomUUID());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetMongo() {
        mongoTemplate.dropCollection(DailyOrderMetrics.class);
        mongoTemplate.dropCollection(StatusCount.class);
        mongoTemplate.dropCollection(CustomerStats.class);
        mongoTemplate.dropCollection(ProcessedEvent.class);
    }

    @Test
    void orderCreatedEvent_endToEnd_visibleViaRestEndpoint() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","status":"UNPROCESSED","totalAmount":42.50,"orderDate":"2026-04-29T10:00:00Z"}
                """.formatted(orderId, customerId);

        produce("ORDER_CREATED", "ob-" + orderId, orderId.toString(), json);

        // Wait for the consumer to apply the event to all three views
        DailyOrderMetrics persistedDay = waitFor(() ->
                mongoTemplate.findById("2026-04-29", DailyOrderMetrics.class));

        // REST endpoint should reflect the same state
        mockMvc.perform(get("/api/v1/analytics/daily")
                        .param("from", "2026-04-29")
                        .param("to", "2026-04-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].day").value("2026-04-29"))
                .andExpect(jsonPath("$[0].ordersCreated").value(1))
                .andExpect(jsonPath("$[0].revenue").value(42.50));

        mockMvc.perform(get("/api/v1/analytics/status-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status=='UNPROCESSED')].count").value(1));

        mockMvc.perform(get("/api/v1/analytics/top-customers").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$[0].totalOrders").value(1))
                .andExpect(jsonPath("$[0].totalSpent").value(42.50));
    }

    private void produce(String eventType, String outboxId, String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafkaContainer.INSTANCE.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>("orders.events", null, key, value);
            record.headers().add(OrderEventConsumer.HEADER_EVENT_TYPE,
                    eventType.getBytes(StandardCharsets.UTF_8));
            record.headers().add(OrderEventConsumer.HEADER_OUTBOX_ID,
                    outboxId.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private <T> T waitFor(java.util.function.Supplier<T> supplier) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        while (System.currentTimeMillis() < deadline) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out waiting for state to materialize");
    }
}
