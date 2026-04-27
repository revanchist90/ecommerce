CREATE TABLE order_lines (
    id          BIGSERIAL     PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID          NOT NULL,
    quantity    INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(19,4) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_lines_order   ON order_lines(order_id);
CREATE INDEX idx_order_lines_product ON order_lines(product_id);
