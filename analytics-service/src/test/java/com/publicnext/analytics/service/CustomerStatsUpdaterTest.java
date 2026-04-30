package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.support.TestMongoContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class CustomerStatsUpdaterTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", TestMongoContainer.INSTANCE::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private CustomerStatsUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new CustomerStatsUpdater(mongoTemplate);
        mongoTemplate.dropCollection(CustomerStats.class);
    }

    @Test
    void onOrderCreated_upsertsCustomer_andSumsTotalSpent() {
        String customerId = "c-1";

        updater.onOrderCreated(customerId, new BigDecimal("12.50"));
        updater.onOrderCreated(customerId, new BigDecimal("7.50"));
        updater.onOrderCreated(customerId, new BigDecimal("30.00"));

        CustomerStats stats = mongoTemplate.findById(customerId, CustomerStats.class);
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalOrders()).isEqualTo(3);
        assertThat(stats.getTotalSpent()).isEqualByComparingTo("50.00");
    }

    @Test
    void differentCustomers_storedAsSeparateDocuments() {
        updater.onOrderCreated("c-a", new BigDecimal("10.00"));
        updater.onOrderCreated("c-b", new BigDecimal("20.00"));

        assertThat(mongoTemplate.findById("c-a", CustomerStats.class).getTotalSpent())
                .isEqualByComparingTo("10.00");
        assertThat(mongoTemplate.findById("c-b", CustomerStats.class).getTotalSpent())
                .isEqualByComparingTo("20.00");
    }

    @Test
    void nullCustomerId_isSkipped() {
        updater.onOrderCreated(null, new BigDecimal("10.00"));

        assertThat(mongoTemplate.findAll(CustomerStats.class)).isEmpty();
    }
}
