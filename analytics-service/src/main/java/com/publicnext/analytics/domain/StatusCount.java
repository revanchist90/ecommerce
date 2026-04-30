package com.publicnext.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "status_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatusCount {

    @Id
    private String status;

    private long count;
}
