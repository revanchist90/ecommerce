package com.publicnext.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Document(collection = "order_metrics_daily")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyOrderMetrics {

    @Id
    private String day;

    private long ordersCreated;

    private long ordersShipped;

    private long ordersCancelled;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal revenue;
}
