package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.CustomerStats;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CustomerStatsUpdater {

    private final MongoTemplate mongoTemplate;

    public void onOrderCreated(String customerId, BigDecimal totalAmount) {
        if (customerId == null) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(customerId));
        Update update = new Update()
                .inc("totalOrders", 1L)
                .inc("totalSpent", totalAmount != null ? totalAmount : BigDecimal.ZERO);
        mongoTemplate.upsert(query, update, CustomerStats.class);
    }
}
