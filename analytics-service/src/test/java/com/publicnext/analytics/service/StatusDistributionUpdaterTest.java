package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.StatusCount;
import com.publicnext.analytics.support.TestMongoContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class StatusDistributionUpdaterTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", TestMongoContainer.INSTANCE::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private StatusDistributionUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new StatusDistributionUpdater(mongoTemplate);
        mongoTemplate.dropCollection(StatusCount.class);
    }

    @Test
    void onOrderCreated_incrementsUnprocessed() {
        updater.onOrderCreated();
        updater.onOrderCreated();
        updater.onOrderCreated();

        assertThat(countFor("UNPROCESSED")).isEqualTo(3);
    }

    @Test
    void onStatusChanged_movesCountFromOneStatusToAnother() {
        updater.onOrderCreated();
        updater.onOrderCreated();
        // counts: UNPROCESSED=2

        updater.onStatusChanged("UNPROCESSED", "PROCESSING");
        // counts: UNPROCESSED=1, PROCESSING=1

        assertThat(countFor("UNPROCESSED")).isEqualTo(1);
        assertThat(countFor("PROCESSING")).isEqualTo(1);

        updater.onStatusChanged("PROCESSING", "SHIPPED");
        // counts: UNPROCESSED=1, PROCESSING=0, SHIPPED=1

        assertThat(countFor("UNPROCESSED")).isEqualTo(1);
        assertThat(countFor("PROCESSING")).isZero();
        assertThat(countFor("SHIPPED")).isEqualTo(1);
    }

    @Test
    void onOrderDeleted_decrementsStatusAtDeletion() {
        updater.onOrderCreated();
        updater.onOrderCreated();
        // UNPROCESSED=2

        updater.onOrderDeleted("UNPROCESSED");
        // UNPROCESSED=1

        assertThat(countFor("UNPROCESSED")).isEqualTo(1);
    }

    @Test
    void onStatusChanged_withNullFromStatus_onlyIncrementsTarget() {
        updater.onStatusChanged(null, "PROCESSING");

        assertThat(countFor("PROCESSING")).isEqualTo(1);
    }

    private long countFor(String status) {
        StatusCount doc = mongoTemplate.findById(status, StatusCount.class);
        return doc == null ? 0 : doc.getCount();
    }
}
