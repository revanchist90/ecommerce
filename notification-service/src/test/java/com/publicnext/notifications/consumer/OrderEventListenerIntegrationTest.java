package com.publicnext.notifications.consumer;

import com.publicnext.notifications.dispatch.NotificationDispatcher;
import com.publicnext.notifications.dispatch.NotificationRequest;
import com.publicnext.notifications.support.TestKafkaContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OrderEventListenerIntegrationTest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", TestKafkaContainer.INSTANCE::getBootstrapServers);
        // Unique consumer group per JVM run so we always start at earliest in this topic
        registry.add("spring.kafka.consumer.group-id", () -> "notification-it-" + UUID.randomUUID());
    }

    @MockBean
    private NotificationDispatcher dispatcher;

    @Value("${orders.kafka.topic}")
    private String topic;

    @Test
    void publishOrderCreated_invokesDispatcherWithFormattedRequest() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","status":"UNPROCESSED","totalAmount":20.00}
                """.formatted(orderId, customerId);

        produce("ORDER_CREATED", orderId.toString(), json);

        verify(dispatcher, timeout(10_000))
                .dispatch(argThat(req -> orderId.toString().equals(req.orderId())));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, atLeastOnce()).dispatch(captor.capture());
        NotificationRequest req = captor.getAllValues().stream()
                .filter(r -> orderId.toString().equals(r.orderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Dispatcher was not invoked with orderId " + orderId));

        assertThat(req.recipient()).isEqualTo("customer:" + customerId);
        assertThat(req.subject()).isEqualTo("Order received");
        assertThat(req.message()).contains(orderId.toString());
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
}
