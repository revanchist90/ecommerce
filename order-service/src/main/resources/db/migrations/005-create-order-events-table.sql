CREATE TABLE order_events (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_type   VARCHAR(50)  NOT NULL CHECK (event_type IN ('ORDER_CREATED','STATUS_CHANGED')),
    from_status  VARCHAR(20)  NULL,
    to_status    VARCHAR(20)  NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_events_order_id    ON order_events(order_id);
CREATE INDEX idx_order_events_occurred_at ON order_events(occurred_at);
