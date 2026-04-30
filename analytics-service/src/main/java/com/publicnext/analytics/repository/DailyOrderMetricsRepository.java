package com.publicnext.analytics.repository;

import com.publicnext.analytics.domain.DailyOrderMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyOrderMetricsRepository extends MongoRepository<DailyOrderMetrics, String> {
}
