# AGENTS.md — GePay Backend Conventions

> **This repository's single source of truth for how code is written.**
> Written for humans **and** AI coding agents. When a habit you have
> contradicts a rule here, **the rule wins**. If the rule seems wrong, change
> the rule (and the code it describes) in the same change — never silently
> deviate.

The detailed, normative rules live in `docs/agents/*.md`. This file is the
**index**: authority, quick facts, golden rules, and a stable section map.
Every `§N` referenced from code (`see §6 of AGENTS.md`) resolves to a section
below, so **section numbers are public API too — do not renumber them**.

---

## Quick facts

| Item | Value |
|---|---|
| Style | Modular monolith (single deployable Spring Boot app) |
| Java | 25 |
| Spring Boot | 4.1.1 (modular starters, e.g. `spring-boot-starter-webmvc`) |
| Spring Modulith | 2.1.1 |
| Jackson | 3 (`tools.jackson.databind`) |
| Build | Maven wrapper (`./mvnw`) |
| Base package | `com.gepe.gepay` |
| Persistence | PostgreSQL + JPA + Flyway |
| Cache / shared store | Redis |
| Scheduler | Quartz (clustered JDBC; no `@Scheduled`) |
| Auth | Firebase Admin SDK + Spring Security |
| IDs | BIGINT internal · UUID v7 user-facing (`UuidCreator.getTimeOrderedEpoch()`) |
| Locales | English (default) + Indonesian (`id`) |

### Modules

| Module | Type | Role | Status |
|---|---|---|---|
| `platform` | OPEN (shared) | web, logging, exception, i18n, config, cache, security, modulith recovery | stable |
| `identity` | CLOSED | Firebase auth enrichment, users, roles, `CurrentUser` | stable |
| `ledger` | CLOSED | double-entry ledger, accounts, journals | stable |
| `payment` | CLOSED | donations, settlement, withdrawal/payout, reconciliation | in progress |

---

## Golden rules (TL;DR)

1. A feature module exposes **only its `api` package**; everything under
   `internal` is private. Other modules may only use `<module>.api`, declared
   via `allowedDependencies = "<module>::api"`. → §2
2. `api` is a pure contract: interfaces, immutable DTO records, event records.
   It must **never** depend on `internal`, Spring Data/JPA, or Web types. → §2
3. `platform` is shared infra, declared `OPEN`; it must **never** depend on a
   feature module. → §2.4
4. `@Transactional` belongs on the **service/facade** boundary — never on
   repositories or controllers. Cross-module calls are plain synchronous
   method calls on the other module's facade bean. → §3
5. Every HTTP response is an envelope: `ApiResponse<T>(message, data)` on
   success, `ErrorResponse(code, message, errors)` on failure, produced by the
   single `GlobalExceptionHandler`. → §6
6. Errors are thrown as `ServiceException(<Module>Error.X, args…)`; `code`
   equals the resolved message key and is stable public API. → §6
7. Enums live in the application only (persisted as `varchar`); never a DB
   enum type. → §3
8. Caching uses **shared Redis only**, configured once in `platform/config`;
   modules declare `CacheSpec` beans. Cache read paths returning api DTO
   records; evict from mutating operations. → §3 (caching)
9. No `@Scheduled` — Quartz clustered jobs only. Event listeners must be
   idempotent (at-least-once delivery). → §3 (events/scheduling), §11
10. Never commit secrets; every value is overridable via `SPRING_*` env vars. → §5
11. `ModularityTests` must stay green — it enforces the module boundaries. → §8
12. Audit fields are declared **manually per entity** — there is deliberately
    no `BaseEntity` (readability over abstraction). → §3 (persistence)

---

## Section map (stable `§N`)

| § | Topic | Detail file(s) |
|---|---|---|
| §1 | Project snapshot & status | [`docs/agents/snapshot.md`](docs/agents/snapshot.md) |
| §2 | Module layout & the `api`/`internal` split (2.1–2.4) | [`docs/agents/architecture.md`](docs/agents/architecture.md) |
| §3 | Implementation conventions (Java) | [`coding-standards.md`](docs/agents/coding-standards.md) · [`persistence.md`](docs/agents/persistence.md) · [`transactions.md`](docs/agents/transactions.md) · [`http-api.md`](docs/agents/http-api.md) · [`caching.md`](docs/agents/caching.md) · [`events-scheduling.md`](docs/agents/events-scheduling.md) |
| §4 | Repository & resources layout | [`repository-layout.md`](docs/agents/repository-layout.md) |
| §5 | Environment & infrastructure | [`environment.md`](docs/agents/environment.md) |
| §6 | i18n & error handling | [`errors-i18n.md`](docs/agents/errors-i18n.md) |
| §7 | Logging & observability | [`logging-observability.md`](docs/agents/logging-observability.md) |
| §8 | Testing & enforced boundaries | [`testing.md`](docs/agents/testing.md) |
| §9 | How to add a new module | [`adding-a-module.md`](docs/agents/adding-a-module.md) |
| §10 | Quick answers (TL;DR) | [`quick-reference.md`](docs/agents/quick-reference.md) |
| §11 | Multi-instance (clustered) operation (11.1–11.2) | [`multi-instance.md`](docs/agents/multi-instance.md) |
| §12 | Security & authentication | [`security.md`](docs/agents/security.md) |
| §13 | Anti-patterns & Definition of Done | [`anti-patterns-dod.md`](docs/agents/anti-patterns-dod.md) |

### Commands

```bash
./mvnw test          # unit + modularity + slice/integration tests
./mvnw verify        # everything, incl. packaging
./mvnw spring-boot:run   # run locally (needs Postgres/Redis up)
```

---

## Maintenance rules for this document

- **Update docs with code, in the same change.** A code change that changes
  behavior described here is incomplete until the docs match.
- **Section numbers are stable.** New material is appended (§12+) or added as
  subsections (`§3.x`); never renumber existing sections.
- Every claim must be verifiable against the code. When in doubt, read the
  code before editing the doc.
- Message keys, error `code` values, and named-interface names are public API:
  changing them is a breaking change and must be a deliberate, documented act.
