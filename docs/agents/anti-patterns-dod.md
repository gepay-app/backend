# §13 — Anti-patterns & Definition of Done

## Anti-patterns (never do these)

- **Cross-module `internal` access** — reach into another module's `internal`
  package. Use its `api` + `allowedDependencies`.
- **`api` depending on `internal` / JPA / Spring / Web types** — `api` is a pure
  contract (§2.1). (`CurrentUser` is the documented exception: interface in `api`,
  Spring impl in `internal`.)
- **`platform` depending on a feature module** — shared infra stays leaf (§2.4).
- **`@Transactional` on controllers or repositories** — it belongs on services (§3).
- **`@Scheduled`** — Quartz clustered jobs only (§3, §11).
- **In-process caching** — shared Redis only (§3 caching).
- **DB `ENUM` types** — persist enums as `varchar` (§3).
- **`UUID.randomUUID()`** — use UUID v7 (`UuidCreator.getTimeOrderedEpoch()`) (§3).
- **Exposing internal `BIGINT` IDs** — use user-facing UUIDs (§3).
- **Hard-coded user-facing strings** — resolve via message source (§6).
- **`System.out` / `@Data` on entities / mutable static state** (§3).
- **Committing secrets** — `.env`, keys, service accounts are git-ignored (§5).
- **Adding a locale to only one bundle** — keep `en`/`id` in lockstep per module (§6).
- **Renaming message keys / error `code` / named interfaces casually** — they are
  public API; change deliberately and document (§6, AGENTS.md index).
- **Nested `@ApplicationModule`** unless a nested boundary is intended (§2.4).
- **Dead/commented-out code left in the tree** — remove it (§3).

## Definition of Done

A change is complete when **all** of the following hold:

1. `./mvnw test` is green, including `ModularityTests.verifyModularity()`.
2. Module boundaries respect §2; no new cross-module `internal` access.
3. Error handling uses `ServiceException`/`ValidationException` + module/global
   `ErrorCode`, with snake_case keys present in **both** `en` and `id` bundles.
4. Responses use the `ApiResponse`/`ErrorResponse` envelope (no ad-hoc bodies).
5. Persistence changes ship with a Flyway migration; entities keep manual audit
   fields; enums are `varchar`.
6. New cache usage declares a `CacheSpec` (api DTO record value type).
7. No `@Scheduled`, no in-process cache, no secrets committed.
8. Docs updated in the **same change** (AGENTS.md index / `docs/agents/*`,
   `snapshot.md` module status) if behavior/conventions changed.
9. Public-contract changes (keys, codes, named interfaces) are deliberate and
   called out in the change description.
