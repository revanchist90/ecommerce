package com.publicnext.orders.service;

import com.publicnext.orders.domain.OutboxEvent;

public interface OutboxPublisher {

    void publish(OutboxEvent event);
}
