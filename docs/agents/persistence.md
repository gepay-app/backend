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

## Entity relations (associations)

Two conventions, applied consistently across modules:

1. **Default: write through the FK `*Id`, read through a separate `LAZY`
   `@ManyToOne`.** Declare the raw FK plus a read-only association:

   ```java
   @Column(name = "gateway_fee_config_id")
   private Long gatewayFeeConfigId;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "gateway_fee_config_id", insertable = false, updatable = false)
   private FeeConfig gatewayFeeConfig;
   ```

   - Services set the `*Id` field (via `create(...)`), **never** the association.
     The service usually holds the id, not the entity, so writing the column
     directly needs **no extra query** and no load/proxy of the related row.
   - `insertable = false, updatable = false` is what makes this work: it keeps
     the association **read-only** so Hibernate's DML uses the `*Id` column and
     ignores the association. It does **not** cause an extra query on save.
   - Keep the association `LAZY` so it never queries unless accessed.
   - Same applies to self-references (e.g. `Journal.reversesJournalId` +
     `Journal.reversesJournal`), where the id is set once.

   **This is a default, not an absolute rule.** A column can have only **one
   writable mapping** — you cannot have both `*Id` and the association writable.
   So decide *which side owns the write*:

   - Prefer the `*Id` side (the default here): explicit, efficient, avoids
     accidental loads and cascade surprises.
   - Deviate only when you always hold the related entity and want natural ORM
     writes: then make the association writable and the `*Id` read-only
     (`insertable = false, updatable = false`), or drop `*Id` entirely — and be
     careful about loads/cascades.
   - FKs that must never change after insert (`Entry.journalId`,
     `PaymentAttempt.paymentId`, `Journal.reversesJournalId`) additionally mark
     the `*Id` field `updatable = false`.

2. **Never add `@OneToMany` to a table that grows without bound.** Collections
   like `payments`, `payment_attempts`, `payouts`, `entries`, `user_roles` grow
   forever; mapping them onto the parent invites accidental full loads and N+1.
   Query the children by FK through a repository method instead.
   - `@OneToMany` is allowed only on **bounded/reference** tables whose child set
     is naturally small (e.g. `Channel` → `channelRoutes`). Keep it `LAZY` anyway.

3. **Cross-module references store only the FK id — no association.**
   Associations must stay inside a module (`ModularityTests` enforces the
   boundary). Example: `Adjustment.journalId` points at `ledger` with no mapping.

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
