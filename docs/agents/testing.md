# §8 — Testing & enforced boundaries

## Modularity (must stay green)

`ModularityTests` runs `ApplicationModules.of(Application.class).verify()` and
writes the documentation snapshot. It enforces:

- modules only depend on declared `allowedDependencies`;
- `api` never depends on `internal`;
- no cross-module access to another module's `internal`.

**A failing `verify()` is a red-line.** Fix the boundary, don't loosen the test.

## Test profile

- Tests that load the Spring context need Postgres + Redis up (localhost). The
  `test` profile supplies localhost/root defaults via `application-test.yaml`.

## Test types

- **Unit tests** — services/validators with mocks (`identity`/`ledger` service &
  validator tests).
- **Slice/integration tests** — repositories/services with a real context where
  meaningful.
- **i18n tests** — assert message keys resolve in `en` and `id` and that module
  bundles stay in lockstep (see `MessageSourceTest`).

## What to assert

- Behavior/outcomes, not implementation details. Prefer asserting returned DTOs,
  thrown `ServiceException` codes, and idempotency over field mocks.
- For the ledger: assert journals are balanced and idempotent (same key → same
  result, no double post).

## Commands

```bash
./mvnw test             # everything
./mvnw test -Dtest=ModularityTests   # boundaries only
```
