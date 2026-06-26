# Mini UPI Payment Gateway — Bank Service

A production-grade simulation of India's UPI banking infrastructure built with Spring Boot 3, PostgreSQL, and Docker. This is the first of five microservices in a complete UPI payment gateway implementation.

---

## What this service does

The Bank Service simulates both the payer and payee bank in a UPI transaction. Every time NPCI routes a payment, it calls this service to debit the payer and credit the payee. This service owns the account ledger, enforces business rules (balance checks, PIN validation, daily limits), and guarantees idempotency at the database level.

It handles the hardest part of any payment system: making sure money is never double-debited, never lost between services, and always fully reversible.

---

## Architecture overview

```
NPCI Switch  ──────────────────────────────────────►  BankController
                                                            │
                                              ┌─────────────┴─────────────┐
                                              │        BankService        │
                                              │  ┌────────────────────┐   │
                                              │  │  processDebit()    │   │
                                              │  │  processCredit()   │   │
                                              │  │  processReversal() │   │
                                              │  └────────────────────┘   │
                                              └─────────────┬─────────────┘
                                                            │
                                              ┌─────────────┴─────────────┐
                                              │      Repository layer     │
                                              │  AccountRepository        │
                                              │  LedgerEntryRepository    │
                                              └─────────────┬─────────────┘
                                                            │
                                              ┌─────────────┴─────────────┐
                                              │     PostgreSQL (Docker)   │
                                              │   accounts table          │
                                              │   ledger_entries table    │
                                              └───────────────────────────┘
```

---

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 24 | LTS-compatible, industry standard |
| Framework | Spring Boot 3.5 | Production-grade, widely used at fintechs |
| Build tool | Gradle Kotlin DSL | Type-safe, modern, readable |
| Database | PostgreSQL | ACID transactions, strong constraint support |
| Migrations | Flyway | Versioned, reproducible schema changes |
| ORM | Spring Data JPA + Hibernate | `@Transactional`, `@Version` optimistic locking |
| Security | BCrypt + HMAC-SHA256 | PIN hashing and inter-service request signing |
| Observability | Spring Actuator + Micrometer | Health checks and Prometheus metrics |

---

## Project structure

```
bank-service/
├── src/
│   ├── main/
│   │   ├── java/com/upi/bank/
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java            # BCryptPasswordEncoder bean
│   │   │   │   └── SecurityConfig.java       # Disable default Spring Security auth
│   │   │   ├── controller/
│   │   │   │   └── BankController.java       # All 7 REST endpoints
│   │   │   ├── service/
│   │   │   │   ├── BankService.java          # Core business logic
│   │   │   │   └── FailureSimulatorService.java  # In-memory failure injection
│   │   │   ├── repository/
│   │   │   │   ├── AccountRepository.java
│   │   │   │   └── LedgerEntryRepository.java
│   │   │   ├── entity/
│   │   │   │   ├── Account.java              # @Version optimistic locking
│   │   │   │   ├── LedgerEntry.java          # Append-only, UNIQUE on transaction_id
│   │   │   │   └── EntryType.java            # DEBIT | CREDIT | REVERSAL
│   │   │   ├── dto/
│   │   │   │   ├── request/                  # Validated request DTOs
│   │   │   │   └── response/                 # Response shapes
│   │   │   ├── exception/                    # Typed business exceptions
│   │   │   ├── filter/
│   │   │   │   └── HmacAuthFilter.java       # Inter-service request signing
│   │   │   ├── scheduler/
│   │   │   │   └── DailyLimitResetJob.java   # Midnight daily limit reset
│   │   │   └── advice/
│   │   │       └── GlobalExceptionHandler.java  # Maps exceptions to HTTP responses
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__create_accounts.sql
│   │           └── V2__create_ledger_entries.sql
│   └── test/
│       └── java/com/upi/bank/              # Unit + Testcontainers integration tests
├── build.gradle.kts
└── docker-compose.yml
```

---

## API reference

### `POST /bank/debit`
Called by NPCI Switch to debit the payer's account.

**Request**
```json
{
  "transaction_id": "uuid",
  "account_vpa": "saad@okaxis",
  "amount_paise": 50000,
  "upi_pin_hash": "bcrypt_hash",
  "hmac_signature": "sha256_hmac"
}
```

**Response**
```json
{
  "status": "SUCCESS",
  "rrn": "RRN1234567890",
  "failure_reason": null
}
```

**Validation order inside `processDebit()`**
1. Verify HMAC signature
2. Check for duplicate `transaction_id` in ledger
3. Find account by VPA
4. Check `pin_locked` status
5. Verify bcrypt PIN — increment attempt count on failure, lock at 3
6. Check `balance_paise >= amount_paise`
7. Check daily limit not exceeded
8. `@Transactional`: debit balance + write `DEBIT` ledger entry

**Failure codes:** `INVALID_PIN` · `ACCOUNT_LOCKED` · `INSUFFICIENT_BALANCE` · `LIMIT_EXCEEDED` · `ACCOUNT_NOT_FOUND` · `409 DUPLICATE`

---

### `POST /bank/credit`
Called by NPCI Switch to credit the payee's account.

**Request**
```json
{
  "transaction_id": "uuid",
  "account_vpa": "riya@okhdfcbank",
  "amount_paise": 50000,
  "hmac_signature": "sha256_hmac"
}
```

No PIN check. No balance check. Idempotent via `UNIQUE` constraint on `transaction_id` in `ledger_entries`.

---

### `POST /bank/reversal`
Called by the Reconciliation Lambda when a debit succeeds but credit times out.

**Request**
```json
{
  "original_txn_id": "uuid",
  "reversal_txn_id": "uuid",
  "account_vpa": "saad@okaxis",
  "amount_paise": 50000
}
```

Writes a `REVERSAL` entry to the ledger using `reversal_txn_id` as the idempotency key. Returns `409` if the reversal was already applied.

---

### `POST /bank/simulate-failure`
Dev/test only. Forces a specific failure on the next debit call for a given VPA.

```json
{
  "vpa": "saad@okaxis",
  "failure_type": "INSUFFICIENT_BALANCE",
  "clear": false
}
```

Supported failure types: `INSUFFICIENT_BALANCE` · `INVALID_PIN` · `LIMIT_EXCEEDED` · `TIMEOUT` · `ACCOUNT_LOCKED`

---

### `GET /bank/account/{vpa}/balance`
Returns current balance, daily limit usage, and lock status. Used by reconciliation and debugging.

---

### `GET /bank/ledger/{txn_id}`
Returns the full audit trail for a transaction — all ledger entries (DEBIT, CREDIT, REVERSAL) in order. The primary tool for investigating disputed transactions.

---

### `POST /bank/seed`
Dev profile only (`@Profile("dev")`). Creates two test accounts:

| VPA | Balance | PIN |
|---|---|---|
| `saad@okaxis` | ₹10,000 | 123456 |
| `riya@okhdfcbank` | ₹5,000 | 654321 |

**Never exposed in production.**

---

## Database schema

### `accounts`

| Column | Type | Notes |
|---|---|---|
| `account_id` | UUID PK | |
| `vpa` | VARCHAR UNIQUE | e.g. `saad@okaxis` |
| `balance_paise` | BIGINT | Never store money as float |
| `daily_limit_paise` | BIGINT | Default ₹1 lakh (10,000,00 paise) |
| `daily_used_paise` | BIGINT | Reset to 0 at midnight |
| `upi_pin_hash` | VARCHAR | bcrypt hash of 6-digit PIN |
| `pin_locked` | BOOLEAN | Locked after 3 wrong attempts |
| `pin_attempt_count` | INTEGER | Reset on successful auth |
| `version` | BIGINT | `@Version` — optimistic locking |

### `ledger_entries`

| Column | Type | Notes |
|---|---|---|
| `entry_id` | UUID PK | |
| `transaction_id` | UUID UNIQUE | Idempotency key — prevents double debit/credit |
| `account_id` | UUID FK | References `accounts` |
| `type` | ENUM | `DEBIT` · `CREDIT` · `REVERSAL` |
| `amount_paise` | BIGINT | |
| `rrn` | VARCHAR | Bank Reference Number |
| `created_at` | TIMESTAMP | Append-only — never updated or deleted |

The `UNIQUE` constraint on `transaction_id` is the last line of defence against double-debiting. Even if NPCI retries a debit request, the database will reject the duplicate insert and roll back the entire transaction.

---

## Key design decisions

**Why `@Transactional(isolation = SERIALIZABLE)` on `processDebit()`**
Two concurrent debit requests for the same account can both pass the balance check before either writes. Serializable isolation prevents this — one wins, the other retries.

**Why `@Version` on `Account`**
Optimistic locking at the JPA layer catches concurrent updates that slip through isolation. An `OptimisticLockException` is caught and returned as a `CONFLICT` response to the caller.

**Why Flyway instead of `ddl-auto=create`**
Flyway owns the schema. Hibernate is set to `validate` only — it checks entity classes match the DB but never changes tables. This makes migrations reproducible and safe across environments.

**Why `BIGINT` in paise, never `DOUBLE` in rupees**
Floating point cannot represent monetary values exactly. `0.1 + 0.2 != 0.3` in IEEE 754. All amounts are stored and passed as `Long` in paise (₹1 = 100 paise) and only converted to rupees for display.

**Why MDC for logging**
Every service method sets `MDC.put("transaction_id", ...)` as its first line. This tags every log line with the transaction ID so a complete payment flow can be traced across all services with a single `grep`.

---

## Running locally

### Prerequisites
- Java 24
- Docker Desktop

### Start PostgreSQL
```bash
docker run --name bankdb \
  -e POSTGRES_DB=bankdb \
  -e POSTGRES_USER=bankuser \
  -e POSTGRES_PASSWORD=bankpass \
  -p 5432:5432 \
  -d postgres:16
```

### Build and run
```bash
./gradlew build -x test
./gradlew bootRun
```

### Seed test data
```bash
curl -X POST http://localhost:3003/bank/seed
```

### Run a test debit
```bash
curl -X POST http://localhost:3003/bank/debit \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
    "account_vpa": "saad@okaxis",
    "amount_paise": 50000,
    "upi_pin_hash": "$2a$10$...",
    "hmac_signature": "test"
  }'
```

---

## Failure scenarios covered

| Code | Scenario | Terminal? |
|---|---|---|
| `INSUFFICIENT_BALANCE` | Payer balance < amount | Yes — no reversal needed |
| `INVALID_PIN` | Wrong PIN entered | Yes — locked after 3 attempts |
| `ACCOUNT_LOCKED` | 3 failed PIN attempts | Yes — resets at midnight |
| `LIMIT_EXCEEDED` | Daily ₹1 lakh limit hit | Yes — resets at midnight |
| `ACCOUNT_NOT_FOUND` | VPA does not exist | Yes |
| `409 DUPLICATE` | Same `transaction_id` retried | Yes — returns existing status |
| `REVERSAL` | Credit timed out after debit success | Handled by Recon Lambda |

---

## Observability

Health check: `GET http://localhost:3003/actuator/health`

Prometheus metrics: `GET http://localhost:3003/actuator/prometheus`

---

## Part of a larger system

This is Service 1 of 5 in the Mini UPI Payment Gateway project:

| Service | Port | Status |
|---|---|---|
| **Bank Service** | 3003 | In progress |
| PSP Service | 3001 | Planned |
| NPCI Switch | 3002 | Planned |
| Notification Service | 3004 | Planned |
| Reconciliation Lambda | AWS | Planned |

---

## Interview talking points

**On idempotency:** "Every money operation has a `transaction_id` that maps to a `UNIQUE` constraint on `ledger_entries`. Even if NPCI retries a debit three times due to a timeout, the database rejects the second and third inserts and rolls back — the account is only debited once."

**On the reversal flow:** "If NPCI debits the payer but the payee bank times out, the Reconciliation Lambda detects the `CREDIT_PENDING` state after 5 minutes and calls `/bank/reversal`. The bank credits the payer back and writes a `REVERSAL` ledger entry — the full audit trail is preserved."

**On concurrency:** "I used `@Version` for optimistic locking and `SERIALIZABLE` isolation on debit. Two concurrent debits that both pass the balance check — one will commit and bump the version, the other hits an `OptimisticLockException` and fails safely."

**On the audit trail:** "`GET /bank/ledger/{txn_id}` returns every ledger entry for a transaction in order. During a disputed payment you can see exactly what debited, what credited, and whether a reversal was applied — all from one query."