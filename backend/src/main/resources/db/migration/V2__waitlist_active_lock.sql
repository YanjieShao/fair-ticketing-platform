-- One waitlist row per user per tier while they are still in the queue.
-- Cancelled and expired entries keep their history; the unique lock is cleared
-- so the same person can join again. MySQL unique indexes ignore NULLs.

ALTER TABLE waitlist_entries
    ADD COLUMN active_lock_key VARCHAR(64) NULL AFTER converted_order_id,
    ADD UNIQUE KEY uk_waitlist_active_lock (active_lock_key);

ALTER TABLE waitlist_entries
    DROP INDEX uk_waitlist_tier_user;
