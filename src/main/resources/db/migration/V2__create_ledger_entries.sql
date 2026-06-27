CREATE TYPE entry_type AS ENUM ('DEBIT', 'CREDIT', 'REVERSAL');

CREATE TABLE ledger_entries (
    entry_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID         NOT NULL UNIQUE,
    account_id     UUID         NOT NULL REFERENCES accounts(account_id),
    type           entry_type   NOT NULL,
    amount_paise   BIGINT       NOT NULL,
    rrn            VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT amount_positive CHECK (amount_paise > 0)
);

CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_account_id     ON ledger_entries(account_id);