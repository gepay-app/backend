# §3 — Events & scheduling

## Domain events

- Event types are `record`s in `<module>.api` (e.g. `api/event/...`) — pure
  contract, immutable, serializable.
- Publish via `ApplicationEvents` (Spring Modulith). Publish **after** the
  transaction commits: use `@TransactionalEventListener(phase = AFTER_COMMIT)`
  on listeners, or publish explicitly post-commit.
- Delivery is **at-least-once** (see §11). **Every listener must be idempotent.**

## Scheduling

- **No `@Scheduled`** anywhere. Use **Quartz** (JDBC clustered) only.
- Jobs extend `QuartzJobBean`; register them via a `@Configuration` exposing
  `JobDetail` + `Trigger` beans (see `platform/modulith/EventPublicationResubmissionScheduler`).
- Mark jobs `@DisallowConcurrentExecution` when concurrent runs would corrupt
  state; use misfire handlers appropriately.

## Event publication recovery

`platform/modulith` owns the crash-recovery machinery (resubmission Quartz job +
`FailedEventPublications`). Listener idempotency is what makes recovery safe.
