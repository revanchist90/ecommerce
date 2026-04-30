package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.domain.StatusCount;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class AnalyticsQueryServiceTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", TestMongoContainer.INSTANCE::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private AnalyticsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AnalyticsQueryService(mongoTemplate);
        mongoTemplate.dropCollection(DailyOrderMetrics.class);
        mongoTemplate.dropCollection(StatusCount.class);
        mongoTemplate.dropCollection(CustomerStats.class);
    }

    @Test
    void dailyBetween_returnsRowsInRange_sortedByDayAsc() {
        mongoTemplate.save(metric("2026-04-27", 1, new BigDecimal("10.00")));
        mongoTemplate.save(metric("2026-04-29", 3, new BigDecimal("30.00")));
        mongoTemplate.save(metric("2026-04-28", 2, new BigDecimal("20.00")));
        mongoTemplate.save(metric("2026-05-01", 9, new BigDecimal("90.00")));

        List<DailyOrderMetrics> result = queryService.dailyBetween(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 4, 30));

        assertThat(result).extracting(DailyOrderMetrics::getDay)
                .containsExactly("2026-04-28", "2026-04-29");
    }

    @Test
    void dailyBetween_emptyWhenNoMatches() {
        mongoTemplate.save(metric("2026-04-27", 1, new BigDecimal("10.00")));

        List<DailyOrderMetrics> result = queryService.dailyBetween(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(result).isEmpty();
    }

    @Test
    void statusDistribution_returnsAllStatusCounts() {
        mongoTemplate.save(new StatusCount("UNPROCESSED", 5L));
        mongoTemplate.save(new StatusCount("PROCESSING", 2L));
        mongoTemplate.save(new StatusCount("SHIPPED", 7L));

        List<StatusCount> result = queryService.statusDistribution();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(StatusCount::getStatus)
                .containsExactlyInAnyOrder("UNPROCESSED", "PROCESSING", "SHIPPED");
    }

    @Test
    void topCustomers_sortedByTotalSpentDesc_andLimited() {
        mongoTemplate.save(new CustomerStats("c-low", 1, new BigDecimal("5.00")));
        mongoTemplate.save(new CustomerStats("c-high", 4, new BigDecimal("400.00")));
        mongoTemplate.save(new CustomerStats("c-mid", 2, new BigDecimal("50.00")));

        List<CustomerStats> result = queryService.topCustomers(2);

        assertThat(result).extracting(CustomerStats::getCustomerId)
                .containsExactly("c-high", "c-mid");
    }

    private DailyOrderMetrics metric(String day, long created, BigDecimal revenue) {
        return new DailyOrderMetrics(day, created, 0, 0, revenue);
    }
}
