# §1 — Project snapshot & status

Current, verifiable state of the codebase. Update this file whenever the
project structure, stack, or module status changes.

## Stack

| Layer | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.1 (modular starters: `webmvc`, `data-jpa`, `data-redis`, `flyway`, `quartz`, `validation`, `security`, `actuator`) |
| Modularity | Spring Modulith 2.1.1 |
| JSON | Jackson 3 (`tools.jackson.databind`) |
| Persistence | PostgreSQL + JPA (Hibernate) + Flyway |
| Cache | Redis (shared store, Spring Cache abstraction) |
| Scheduler | Quartz (JDBC clustered) |
| Auth | Firebase Admin SDK + Spring Security |
| IDs | BIGINT internal · UUID v7 user-facing (`com.github.f4b6a3:uuid-creator`) |
| Build | Maven wrapper |

## Base package

`com.gepe.gepay` — the application root (`Application`), each feature module,
and `platform` all live under it.

## Modules

| Module | Package | Type | Status |
|---|---|---|---|
| `platform` | `com.gepe.gepay.platform` | OPEN (shared) | stable — web, logging, exception, i18n, config, cache, security, modulith recovery |
| `identity` | `com.gepe.gepay.identity` | CLOSED | stable — Firebase auth enrichment, users, roles, `CurrentUser` |
| `ledger` | `com.gepe.gepay.ledger` | CLOSED | stable — double-entry ledger, accounts, journals |
| `payment` | `com.gepe.gepay.payment` | CLOSED | in progress — generic payment engine: payin, settlement, withdrawal/payout, reconciliation |

## Database migrations (Flyway, `src/main/resources/db/migration`)

| Version | Content |
|---|---|
| `V1__event_publication.sql` | Spring Modulith event publication table |
| `V2__quartz_tables.sql` | Quartz `QRTZ_*` tables (clustered JDBC store) |
| `V3__identity_tables.sql` | `identity` users, roles, user_roles |
| `V4__ledger_tables.sql` | `ledger` accounts, journals, entries |
| `V5__payment_tables.sql` | `payment` schema (WIP: payments, attempts, settlements, withdrawals, payouts, fund transfers, refunds, adjustments, channels, fee configs, holidays, …) |

## What is (not) there yet

- **Present:** full `platform` + `identity` + `ledger`; `payment` has entities,
  enums, and `BusinessDayCalculator` only (no facade/controller/error enum/i18n yet).
- **Planned / not yet done:** `platform/persistence` was considered for
  auditing but is intentionally **not** introduced (audit fields are manual —
  see §3 persistence); metrics/tracing bridge (tracing off in `application.yaml`);
  root `Dockerfile`/compose; OpenAPI docs.
