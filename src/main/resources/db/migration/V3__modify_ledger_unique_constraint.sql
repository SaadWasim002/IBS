ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_transaction_id_key;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_transaction_id_type_key UNIQUE (transaction_id, type);
