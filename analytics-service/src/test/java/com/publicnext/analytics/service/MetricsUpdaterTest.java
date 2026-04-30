package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.support.TestMongoContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class MetricsUpdaterTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", TestMongoContainer.INSTANCE::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private MetricsUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new MetricsUpdater(mongoTemplate);
        mongoTemplate.dropCollection(DailyOrderMetrics.class);
    }

    @Test
    void onOrderCreated_upsertsAndIncrementsCount_andSumsRevenue() {
        LocalDate day = LocalDate.of(2026, 4, 29);

        updater.onOrderCreated(day, new BigDecimal("10.00"));
        updater.onOrderCreated(day, new BigDecimal("5.50"));
        updater.onOrderCreated(day, new BigDecimal("2.50"));

        DailyOrderMetrics metrics = mongoTemplate.findById(day.toString(), DailyOrderMetrics.class);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getOrdersCreated()).isEqualTo(3);
        assertThat(metrics.getRevenue()).isEqualByComparingTo("18.00");
        assertThat(metrics.getOrdersShipped()).isZero();
        assertThat(metrics.getOrdersCancelled()).isZero();
    }

    @Test
    void onOrderShipped_incrementsShippedCounter_independentOfCreated() {
        LocalDate day = LocalDate.of(2026, 4, 29);

        updater.onOrderShipped(day);
        updater.onOrderShipped(day);

        DailyOrderMetrics metrics = mongoTemplate.findById(day.toString(), DailyOrderMetrics.class);
        assertThat(metrics.getOrdersShipped()).isEqualTo(2);
        assertThat(metrics.getOrdersCreated()).isZero();
    }

    @Test
    void onOrderCancelled_incrementsCancelledCounter() {
        LocalDate day = LocalDate.of(2026, 4, 29);

        updater.onOrderCancelled(day);

        DailyOrderMetrics metrics = mongoTemplate.findById(day.toString(), DailyOrderMetrics.class);
        assertThat(metrics.getOrdersCancelled()).isEqualTo(1);
    }

    @Test
    void differentDays_storedAsSeparateDocuments() {
        LocalDate d1 = LocalDate.of(2026, 4, 28);
        LocalDate d2 = LocalDate.of(2026, 4, 29);

        updater.onOrderCreated(d1, new BigDecimal("10.00"));
        updater.onOrderCreated(d2, new BigDecimal("20.00"));

        assertThat(mongoTemplate.findById(d1.toString(), DailyOrderMetrics.class).getRevenue())
                .isEqualByComparingTo("10.00");
        assertThat(mongoTemplate.findById(d2.toString(), DailyOrderMetrics.class).getRevenue())
                .isEqualByComparingTo("20.00");
    }
}
