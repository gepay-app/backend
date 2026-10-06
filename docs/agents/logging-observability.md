# §7 — Logging & observability

## MDC correlation

- `CorrelationIdFilter` (`platform.web`) assigns a `requestId` per request and
  stores it in the MDC (`platform.logging.MdcKeys`); it removes it in a `finally`.
- Every log line during a request carries the `requestId` — include it in the
  logback pattern.

## Levels

- `debug` — expected business outcomes, validation failures, per-request detail.
- `warn` — contract violations without a stack trace (bad request shape, missing
  message key, stale state).
- `error` — unexpected failures **with** the full stack trace (the fallback 500
  handler).

## Rules

- Use `@Slf4j`; log with parameterized placeholders, never string concatenation.
- **Never log secrets** (tokens, credentials, full auth headers).
- **Never log PII** unmasked; mask `ownerRef`/user identifiers where required.
- Don't render exception text to clients — the handler resolves localized keys
  (see §6); exception messages are for logs only.

## Structured JSON

- A logback `json` profile (logstash-logback-encoder) emits structured JSON;
  activate with `SPRING_PROFILES_ACTIVE=json`.

## Metrics & tracing

- Actuator is present; only `health` is exposed by default (readiness/DB/Redis).
- Metrics/tracing bridge is **planned but off** (`management.tracing.enabled: false`).
  Do not rely on it yet.
