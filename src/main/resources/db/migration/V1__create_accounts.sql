CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    account_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    vpa               VARCHAR(100)  NOT NULL UNIQUE,
    balance_paise     BIGINT        NOT NULL DEFAULT 0,
    daily_limit_paise BIGINT        NOT NULL DEFAULT 10000000,
    daily_used_paise  BIGINT        NOT NULL DEFAULT 0,
    upi_pin_hash      VARCHAR(255)  NOT NULL,
    pin_locked        BOOLEAN       NOT NULL DEFAULT FALSE,
    pin_attempt_count INT           NOT NULL DEFAULT 0,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT balance_non_negative      CHECK (balance_paise >= 0),
    CONSTRAINT daily_used_non_negative   CHECK (daily_used_paise >= 0),
    CONSTRAINT daily_limit_positive      CHECK (daily_limit_paise > 0)
);

CREATE INDEX idx_accounts_vpa ON accounts(vpa);