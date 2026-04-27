CREATE TABLE orders (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        UUID         NOT NULL UNIQUE,
    customer_id     UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('UNPROCESSED','PROCESSING','PROCESSED','SHIPPED')),
    order_date      TIMESTAMPTZ  NOT NULL,
    total_amount    NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_status      ON orders(status)      WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_order_date  ON orders(order_date);
