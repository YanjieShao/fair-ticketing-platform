-- Fair Ticketing Platform - initial schema
-- All timestamps are stored in UTC. Venue-local time is derived from venues.timezone.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE artists (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(200) NOT NULL,
    genre            VARCHAR(50)  NOT NULL,
    -- 0..100, a feature for demand forecasting
    popularity_score INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_artists_name (name),
    KEY idx_artists_genre (genre)
) ENGINE = InnoDB;

CREATE TABLE venues (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(200) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    country  VARCHAR(100) NOT NULL,
    capacity INT          NOT NULL,
    timezone VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_venues_city (city)
) ENGINE = InnoDB;

CREATE TABLE events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id      BIGINT       NOT NULL,
    venue_id       BIGINT       NOT NULL,
    title          VARCHAR(255) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    starts_at      DATETIME(6)  NOT NULL,
    sales_start_at DATETIME(6)  NOT NULL,
    sales_end_at   DATETIME(6)  NOT NULL,
    -- opt-in virtual waiting room, driven by demand forecast
    waiting_room_enabled BIT(1)  NOT NULL DEFAULT b'0',
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_events_status_sales_start (status, sales_start_at),
    KEY idx_events_starts_at (starts_at),
    CONSTRAINT fk_events_artist FOREIGN KEY (artist_id) REFERENCES artists (id),
    CONSTRAINT fk_events_venue FOREIGN KEY (venue_id) REFERENCES venues (id)
) ENGINE = InnoDB;

CREATE TABLE ticket_tiers (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_id          BIGINT       NOT NULL,
    name              VARCHAR(100) NOT NULL,
    price_cents       INT          NOT NULL,
    currency          CHAR(3)      NOT NULL DEFAULT 'EUR',
    total_quantity    INT          NOT NULL,
    -- reserved = held by an active order (paid or awaiting payment)
    reserved_quantity INT          NOT NULL DEFAULT 0,
    max_per_user      INT          NOT NULL DEFAULT 4,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tier_event_name (event_id, name),
    CONSTRAINT fk_tiers_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT ck_tiers_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= total_quantity)
) ENGINE = InnoDB;

CREATE TABLE orders (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(32) NOT NULL,
    user_id          BIGINT      NOT NULL,
    event_id         BIGINT      NOT NULL,
    tier_id          BIGINT      NOT NULL,
    quantity         INT         NOT NULL,
    unit_price_cents INT         NOT NULL,
    total_cents      INT         NOT NULL,
    status           VARCHAR(20) NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    -- Holds 'userId:eventId' while the order occupies inventory, NULL once it does not.
    -- MySQL unique indexes ignore NULLs, so this enforces "one active order per user per event".
    active_lock_key  VARCHAR(64) NULL,
    created_at       DATETIME(6) NOT NULL,
    expires_at       DATETIME(6) NULL,
    paid_at          DATETIME(6) NULL,
    completed_at     DATETIME(6) NULL,
    closed_at        DATETIME(6) NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    UNIQUE KEY uk_orders_idempotency (idempotency_key),
    UNIQUE KEY uk_orders_active_lock (active_lock_key),
    KEY idx_orders_user_created (user_id, created_at),
    KEY idx_orders_status_expires (status, expires_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_orders_tier FOREIGN KEY (tier_id) REFERENCES ticket_tiers (id)
) ENGINE = InnoDB;

CREATE TABLE payments (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    order_id     BIGINT      NOT NULL,
    provider_ref VARCHAR(64) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    amount_cents INT         NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_provider_ref (provider_ref),
    KEY idx_payments_order (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;

CREATE TABLE waitlist_entries (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    event_id            BIGINT      NOT NULL,
    tier_id             BIGINT      NOT NULL,
    user_id             BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL,
    requested_quantity  INT         NOT NULL,
    -- monotonically increasing per tier, decides FIFO fairness
    position_seq        BIGINT      NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    offered_at          DATETIME(6) NULL,
    offer_expires_at    DATETIME(6) NULL,
    converted_order_id  BIGINT      NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_waitlist_tier_user (tier_id, user_id),
    UNIQUE KEY uk_waitlist_tier_position (tier_id, position_seq),
    KEY idx_waitlist_status_offer_expiry (status, offer_expires_at),
    CONSTRAINT fk_waitlist_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_waitlist_tier FOREIGN KEY (tier_id) REFERENCES ticket_tiers (id),
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

-- Append-only record of every inventory movement; the source of truth for reconciliation
-- between Redis counters and the database.
CREATE TABLE inventory_ledger (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    tier_id    BIGINT      NOT NULL,
    order_id   BIGINT      NULL,
    delta      INT         NOT NULL,
    reason     VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ledger_tier_created (tier_id, created_at),
    CONSTRAINT fk_ledger_tier FOREIGN KEY (tier_id) REFERENCES ticket_tiers (id)
) ENGINE = InnoDB;

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    type         VARCHAR(50)  NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    -- structured evidence backing this notification, keeps AI output explainable
    payload_json JSON         NULL,
    source_type  VARCHAR(30)  NOT NULL,
    generated_by VARCHAR(20)  NOT NULL,
    dedupe_key   VARCHAR(120) NOT NULL,
    read_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_dedupe (dedupe_key),
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT      NULL,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT      NULL,
    detail_json JSON        NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_entity (entity_type, entity_id),
    KEY idx_audit_created (created_at)
) ENGINE = InnoDB;

-- Written by the scheduled batch job that calls the Python model service.
-- Never read on the checkout hot path.
CREATE TABLE demand_forecasts (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    event_id        BIGINT      NOT NULL,
    expected_demand INT         NOT NULL,
    capacity        INT         NOT NULL,
    demand_ratio    DECIMAL(8, 3) NOT NULL,
    risk_level      VARCHAR(20) NOT NULL,
    model_version   VARCHAR(40) NOT NULL,
    generated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_forecast_event_generated (event_id, generated_at),
    CONSTRAINT fk_forecast_event FOREIGN KEY (event_id) REFERENCES events (id)
) ENGINE = InnoDB;

CREATE TABLE ai_insights (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    scope_type   VARCHAR(30) NOT NULL,
    scope_id     BIGINT      NULL,
    content      TEXT        NOT NULL,
    payload_json JSON        NULL,
    generated_by VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_insight_scope (scope_type, scope_id, created_at)
) ENGINE = InnoDB;
