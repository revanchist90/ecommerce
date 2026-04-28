CREATE TABLE inventory (
    product_id      UUID         PRIMARY KEY,
    available_stock INTEGER      NOT NULL CHECK (available_stock >= 0),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
