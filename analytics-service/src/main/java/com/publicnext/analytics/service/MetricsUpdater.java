package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.DailyOrderMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MetricsUpdater {

    private final MongoTemplate mongoTemplate;

    public void onOrderCreated(LocalDate day, BigDecimal totalAmount) {
        Update update = new Update()
                .inc("ordersCreated", 1L)
                .inc("revenue", totalAmount);
        upsert(day, update);
    }

    public void onOrderShipped(LocalDate day) {
        upsert(day, new Update().inc("ordersShipped", 1L));
    }

    public void onOrderCancelled(LocalDate day) {
        upsert(day, new Update().inc("ordersCancelled", 1L));
    }

    private void upsert(LocalDate day, Update update) {
        Query query = Query.query(Criteria.where("_id").is(day.toString()));
        mongoTemplate.upsert(query, update, DailyOrderMetrics.class);
    }
}
