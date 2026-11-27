-- ---------------------------------------------------------------------
-- A TokensEmitted / TokensRedeemed log whose bytes32 order id is not a
-- core-api UUID (first 16 bytes not zero) has no order id. It is still stored
-- and published with orderId null for manual review (docs/events.md,
-- "Order id: UUID <-> bytes32"), so order_id must accept NULL.
-- ---------------------------------------------------------------------
ALTER TABLE observed_events ALTER COLUMN order_id DROP NOT NULL;
