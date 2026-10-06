# §11 — Multi-instance (clustered) operation

The app runs as **multiple instances** against one PostgreSQL and one Redis.
Everything that must be "exactly-once across instances" is delegated to the DB
or the shared store; in-process state is never the source of truth.

## §11.1 Scheduling & jobs

- Quartz uses a **JDBC clustered store** (`spring.quartz.job-store-type: jdbc`,
  `org.quartz.jobStore.isClustered: true`), `initialize-schema: never`
  (tables from `V2__quartz_tables.sql`).
- Each trigger fires on exactly **one** instance; `overwrite-existing-jobs: true`
  keeps re-registration idempotent across restarts.
- No `@Scheduled`; any periodic work is a Quartz job (see §3 events/scheduling).

## §11.2 Shared state, events & recovery

- **Cache** is shared Redis (see §3 caching); eviction is transaction-aware.
- **Events** (Spring Modulith): publications are stored in `event_publication`.
  On startup every instance republishes outstanding (non-`COMPLETED`)
  publications (`republish-outstanding-events-on-restart: true`), and a staleness
  monitor marks stuck publications `FAILED`. `platform/modulith`'s resubmission
  Quartz job re-delivers `FAILED` publications. **Delivery is at-least-once —
  listeners must be idempotent.**
- **Graceful shutdown**: `server.shutdown: graceful` +
  `spring.lifecycle.timeout-per-shutdown-phase: 30s`; Quartz waits for in-flight
  jobs on shutdown.
- **Flyway** serializes migrations via PostgreSQL advisory locks, so concurrent
  instance starts are safe.

## Correctness anchors

- Use DB unique constraints (idempotency keys) as the **final** guard against
  duplicates, not just application-level pre-checks.
- Use `@Version` optimistic locking (or deliberate row locks) for concurrent
  writes — never rely on instance-local locks.
