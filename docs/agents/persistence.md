# §3 — Persistence

JPA + PostgreSQL + Flyway. Schema is owned **exclusively by Flyway**.

## Entities

- Live in `<module>.internal.entity`; never in `api`.
- `@Entity`, `@Table(name = "snake_case_plural")`. Avoid a table literally named
  `user` (Postgres reserved word) — use `users` (see `identity`).
- **Audit fields are declared manually per entity.** There is deliberately **no
  `BaseEntity`/`@MappedSuperclass`**: readability and explicitness beat
  abstraction here. Repeat `createdAt`/`updatedAt` (and a `@Version` optimistic
  lock field) where needed.
- Do not use Lombok `@Data`/`@EqualsAndHashCode` on entities (proxy/hash pitfalls);
  use explicit `@Getter` and a hand-written `equals`/`hashCode` keyed on the
  identity if ever required.
- Static factory methods (`create(...)`) for construction invariants; keep the
  no-args constructor protected/private as JPA requires.

## Flyway

- One migration file per logical change under `src/main/resources/db/migration`,
  named `V<N>__snake_case_description.sql`.
- `spring.jpa.hibernate.ddl-auto: none` — Hibernate never creates/alters schema.
- Migrations must be **repeatable on a fresh DB and idempotent on re-run**; no
  data fixtures that assume a specific environment.

## Identifiers

- Internal PKs: `BIGINT` `@GeneratedValue`.
- User-facing IDs: `UUID` v7 (`UuidCreator.getTimeOrderedEpoch()`), stored as
  `uuid` columns. See §3 (coding standards).

## Locking & concurrency

- Use `@Version` optimistic locking on entities with write contention; let
  `GlobalExceptionHandler` map `OptimisticLockingFailureException` → 409.
- For the ledger balance path, deterministic ordering + batched pessimistic
  locking is used to prevent deadlocks (see `ledger`'s `LedgerService`); do not
  copy that pattern blindly — prefer optimistic locking unless correctness
  requires row locks.

## Repositories

- Spring Data interfaces in `<module>.internal.repository`.
- Return **entities** from repositories; convert to DTO in the service/mapper.
- Add `@EntityGraph`/`JOIN FETCH` when loading associations to avoid N+1.

## Enums in the DB

- Persist enum as `varchar` (never a DB enum type). See §3 (coding standards).
