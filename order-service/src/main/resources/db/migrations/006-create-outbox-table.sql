CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ  NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT         NULL
);

CREATE INDEX idx_outbox_unpublished
    ON outbox(created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_aggregate
    ON outbox(aggregate_type, aggregate_id);
