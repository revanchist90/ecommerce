package com.publicnext.notifications.consumer;

import com.publicnext.notifications.dispatch.NotificationDispatcher;
import com.publicnext.notifications.support.TestKafkaContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "notifications.retry.attempts=3",
        "notifications.retry.delay-ms=50",
        "notifications.retry.multiplier=2"
})
class DeadLetterTopologyTest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", TestKafkaContainer.INSTANCE::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "notification-dlt-it-" + UUID.randomUUID());
    }

    @MockBean
    private NotificationDispatcher dispatcher;

    @Value("${orders.kafka.topic}")
    private String topic;

    @Test
    void bothGatewaysFail_messageRetriesThenLandsOnDlt() throws Exception {
        when(dispatcher.dispatch(any())).thenReturn(NotificationDispatcher.DispatchOutcome.FAILED);

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","status":"UNPROCESSED","totalAmount":10.00}
                """.formatted(orderId, customerId);

        produce("ORDER_CREATED", orderId.toString(), json);

        ConsumerRecord<String, String> dltRecord = consumeFromDlt(topic + "-dlt", orderId);

        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo(orderId.toString());
        assertThat(headerValue(dltRecord, OrderEventListener.HEADER_EVENT_TYPE))
                .isEqualTo("ORDER_CREATED");

        // Listener should have been invoked once per attempt (initial + 2 retries)
        verify(dispatcher, timeout(5_000).atLeast(3)).dispatch(any());
    }

    private void produce(String eventType, String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafkaContainer.INSTANCE.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value);
            record.headers().add(OrderEventListener.HEADER_EVENT_TYPE,
                    eventType.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private ConsumerRecord<String, String> consumeFromDlt(String dltTopic, UUID expectedKey) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafkaContainer.INSTANCE.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(dltTopic));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
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
