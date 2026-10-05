# Finishing Tasks — GePay Backend

> Checklist before production-ready. Prioritas: **P0 = blocker**, **P1 = high**, **P2 = medium**, **P3 = nice-to-have**.

---

## 1. Indexing & Pagination (P0)

- [ ] **Pageable indexes** — add composite indexes for common `Pageable` queries:
  - `payment.payments`: `(user_id, created_at DESC)`, `(status, created_at DESC)`
  - `payment.settlements`: `(status, expected_settlement_date)`
  - `payment.withdrawals`: `(user_id, status, created_at DESC)`
  - `payment.payouts`: `(withdrawal_id, status)`, `(provider_id, status, created_at DESC)`
  - `ledger.journals`: `(reference_type, reference_id, occurred_at DESC)`
  - `ledger.entries`: `(account_id, id)` — ✅ already in V4

- [ ] **Cursor-based pagination** untuk high-volume endpoints (settlement reconciliation, payout history) — avoid `OFFSET` on large tables

- [ ] **Covering indexes** untuk query read-heavy:
  - `payment.payments`: INCLUDE `(gross_amount, net_creator_amount, expected_settlement_amount)` untuk list tanpa lookup

---

## 2. Cleanup Unused Code (P1)

### Enum / DTO / Function yang tidak kepakai

| Module | Item | Status | Action |
|--------|------|--------|--------|
| `ledger` | `PostJournalRequest` | ❌ unused | Hapus (payment module build lines manual) |
| `ledger` | `LedgerEvent` (api/event) | ❌ unused | Hapus (future only) |
| `ledger` | `internal/listener/` | ❌ empty | Hapus package |
| `ledger` | `internal/delivery/http/` | ❌ empty | Hapus package (admin endpoints nanti) |
| `payment` | `internal/delivery/http/` | ❌ empty | Hapus package |
| `payment` | `internal/exception/` | ❌ empty | Hapus package |
| `payment` | `internal/listener/` | ❌ empty | Hapus package |
| `platform` | `platform/modulith/EventPublicationResubmissionScheduler` | ❌ duplicate | Cek apakah masih dipakai vs Quartz job |

### Dead Code Detection

- [ ] Run `./mvnw spotbugs:check` / `sonar` untuk detect unused methods
- [ ] Remove `@Deprecated` yang sudah tidak dipakai
- [ ] Cek `User` module (contoh) — ada field/method yang tidak terpakai?

---

## 3. Query & Cache Optimization (P1)

### Query Optimization

- [ ] **N+1 prevention** — add `@EntityGraph` / `JOIN FETCH` di repository methods yang return DTO:
  - `JournalRepository.findByReferenceTypeAndReferenceId` → fetch entries
  - `PaymentRepository.findByUserId` → fetch attempts, settlement

- [ ] **Projection/DTO query** — ganti `findAll` return entity dengan `@Query` return record/DTO untuk list endpoints:
  - `PaymentSummaryProjection(id, status, grossAmount, netCreatorAmount, createdAt)`

- [ ] **Settlement due query** — optimize `ix_settlements_due (status, expected_settlement_date)`:
  - Add partial index: `WHERE status IN ('EXPECTED', 'OVERDUE')` (Postgres 12+)

- [ ] **Fund transfer variance** — add index on `payment.fund_transfers(variance_amount)` where `variance_amount != 0`

### Cache Optimization

- [ ] **Cache key strategy** — review `ledger.accountBalance` key:
  - Current: `#code.code + ':' + #ownerRef` → OK
  - Consider: prefix `ledger:balance:` untuk namespacing

- [ ] **Cache warming** — pre-load global accounts (2300, 4000, 4100, 5xxx) at startup via `CommandLineRunner`

- [ ] **TTL tuning** — per account type:
  - Global accounts (VAT, Revenue, Expense): 10 min (jarang berubah)
  - Provider/User accounts: 2 min (sering update)
  - Transit accounts (1400): 30 sec (real-time tracking)

- [ ] **Cache eviction verification** — integration test:
  - Post journal → verify cache evicted within 100ms
  - Multi-instance: instance A write, instance B read → eventual consistency OK

- [ ] **Redis connection pool** — tune `spring.data.redis.lettuce.pool`:
  - `max-active: 50`, `max-idle: 20`, `min-idle: 5`

---

## 4. Observability & Reliability (P1)

- [ ] **Structured logging** — verify JSON profile works (`SPRING_PROFILES_ACTIVE=json`)
- [ ] **Metrics** — enable Micrometer + Prometheus (planned in AGENTS.md §7)
- [ ] **Health checks** — custom `LedgerHealthIndicator` (verify DB connection, balance sanity)
- [ ] **Circuit breaker** — Resilience4j untuk external PG calls (Midtrans, Flip)

---

## 5. Security & Compliance (P2)

- [ ] **Spring Security** — add when starter ready (AGENTS.md §1)
- [ ] **Audit log** — immutable log untuk semua journal post (siapa, kapan, apa)
- [ ] **PII masking** — log masking untuk `ownerRef` (userId) di production

---

## 6. Testing Gaps (P2)

- [ ] **Contract test** — `LedgerApi` contract test (consumer-driven) untuk `payment` module
- [ ] **Chaos test** — kill instance mid-journal, verify idempotency + recovery
- [ ] **Load test** — 1000 TPS journal posting, verify <50ms p99
- [ ] **Reconciliation test** — simulate mismatch, verify auto-detection

---

## 7. Documentation (P3)

- [ ] **API docs** — OpenAPI/Swagger untuk `LedgerApi` + `PaymentApi`
- [ ] **Runbook** — settlement reconciliation, fund transfer stuck, balance mismatch
- [ ] **ADR** — record key decisions (lazy account, dual-write balance, idempotency key format)

---

## Quick Commands

```bash
# Run all tests
./mvnw test

# Check modularity
./mvnw test -Dtest=ModularityTests

# SpotBugs
./mvnw spotbugs:check

# Dependency check
./mvnw dependency:analyze

# Verify no circular deps
./mvnw verify
```

---

## Priority Order

| Priority | Tasks |
|----------|-------|
| **Week 1** | Indexing & Pagination, Cleanup Unused Code |
| **Week 2** | Query & Cache Optimization |
| **Week 3** | Observability, Security, Testing Gaps |
| **Ongoing** | Documentation |

---

*Generated: $(date +%Y-%m-%d)*
*Update saat task selesai: `✅` atau `❌`*