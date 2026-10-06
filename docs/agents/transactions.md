# §3 — Transactions

## Boundary

- `@Transactional` belongs on the **service/facade** layer — never on
  repositories and never on controllers.
- The `api` facade implementation (or a module service) is the transactional
  boundary; controllers stay thin and non-transactional.

## Cross-module calls

- Cross-module calls are **plain synchronous method calls** on the other
  module's facade bean (e.g. `payment` calls `ledgerApi.postJournal(...)`).
- Do **not** create a new transaction for the callee; it participates in the
  caller's transaction. This means the callee must not commit side effects the
  caller later rolls back in a surprising way — keep each facade method
  self-consistent.
- No distributed transaction manager; the monolith shares one DataSource.

## Reads

- Mark read-only methods `@Transactional(readOnly = true)`.
- Cached read paths should return api DTO records (see §3 caching); mutating
  paths evict.

## Practical rules

- Use `saveAndFlush` (not just `save`) when a subsequent DB unique constraint or
  query within the same transaction must observe the write.
- Catch `DataIntegrityViolationException` for race-tolerant upserts (see
  `ledger`'s idempotency handling) rather than relying on pre-check only.
- Keep transactions short: no remote I/O, no `Thread.sleep`, no message
  publishing inside the transaction boundary — publish events **after** commit
  (see §3 events/scheduling).
