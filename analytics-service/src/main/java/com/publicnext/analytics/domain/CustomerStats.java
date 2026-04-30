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

@Document(collection = "customer_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStats {

    @Id
    private String customerId;

    private long totalOrders;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalSpent;
}
