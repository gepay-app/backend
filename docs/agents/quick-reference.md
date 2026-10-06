# §10 — Quick answers (TL;DR)

> Fast lookups. Detail is in the referenced sections.

- **How do modules talk to each other?** Only via `<module>.api`, declared as
  `allowedDependencies = "<module>::api"`; call the facade bean synchronously (§2, §3).
- **Where does business logic live?** In `internal.service` behind the `api`
  facade; controllers are thin (§2, §3 HTTP).
- **Where do I put a new error?** Module enum implementing `ErrorCode` + snake_case
  key in that module's `messages[_id].properties` (§6).
- **How are responses shaped?** `ApiResponse(message, data)` success;
  `ErrorResponse(code, message, errors)` failure (§6).
- **Can I use `@Scheduled`?** No — Quartz clustered jobs only (§3 events/scheduling).
- **Can I cache in-process?** No — shared Redis, declared via `CacheSpec` (§3 caching).
- **Entity ID type?** Internal `BIGINT`; user-facing `UUID` v7 (§3).
- **Enum in the DB?** Always `varchar`, never a DB enum (§3).
- **Base entity for audit fields?** No — manual audit fields per entity (§3 persistence).
- **How do I read the current user?** Inject `identity.api.CurrentUser` (§12).
- **How do I add a locale?** Add the bundle to **every** module, not just one (§6).
- **How do I run tests?** `./mvnw test` with local Postgres/Redis up (§8).
