package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.domain.StatusCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final MongoTemplate mongoTemplate;

    public List<DailyOrderMetrics> dailyBetween(LocalDate from, LocalDate to) {
        Query query = Query.query(
                        Criteria.where("_id").gte(from.toString()).lte(to.toString()))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        return mongoTemplate.find(query, DailyOrderMetrics.class);
    }

    public List<StatusCount> statusDistribution() {
        return mongoTemplate.findAll(StatusCount.class);
    }

    public List<CustomerStats> topCustomers(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "totalSpent"))
                .limit(limit);
        return mongoTemplate.find(query, CustomerStats.class);
    }
}
