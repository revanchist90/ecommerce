ALTER TABLE order_events
    DROP CONSTRAINT IF EXISTS order_events_event_type_check;

ALTER TABLE order_events
    ADD CONSTRAINT order_events_event_type_check
    CHECK (event_type IN ('ORDER_CREATED','STATUS_CHANGED','ORDER_UPDATED','ORDER_DELETED'));
