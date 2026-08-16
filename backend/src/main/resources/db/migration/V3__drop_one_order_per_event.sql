-- A buyer may place another order for the same event (add tickets, or a
-- second tier) as long as the per-tier cap still holds. The unique lock
-- made "I bought one and want two more" require a return-and-rebuy.
ALTER TABLE orders DROP INDEX uk_orders_active_lock;
