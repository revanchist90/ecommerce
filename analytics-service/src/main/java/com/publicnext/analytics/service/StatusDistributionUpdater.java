package com.publicnext.analytics.service;

import com.publicnext.analytics.domain.StatusCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatusDistributionUpdater {

    private static final String UNPROCESSED = "UNPROCESSED";

    private final MongoTemplate mongoTemplate;

    public void onOrderCreated() {
        increment(UNPROCESSED, 1L);
    }

    public void onStatusChanged(String fromStatus, String toStatus) {
        if (fromStatus != null) {
            increment(fromStatus, -1L);
        }
        if (toStatus != null) {
            increment(toStatus, 1L);
        }
    }

    public void onOrderDeleted(String statusAtDeletion) {
        if (statusAtDeletion != null) {
            increment(statusAtDeletion, -1L);
        }
    }

    private void increment(String status, long delta) {
        Query query = Query.query(Criteria.where("_id").is(status));
        Update update = new Update().inc("count", delta);
        mongoTemplate.upsert(query, update, StatusCount.class);
    }
}
