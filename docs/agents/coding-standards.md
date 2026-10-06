# §3 — Implementation conventions (Java)

Core Java style and naming for every module. Persistence, transactions, HTTP,
caching, and events/scheduling have their own §3 sub-docs (see the index).

## Language & style

- Java 25. Use records, sealed interfaces, `switch` expressions, and pattern
  matching where they improve readability.
- **Lombok** is available and used for boilerplate (`@Getter`, `@RequiredArgsConstructor`,
  `@Slf4j`). Do not use `@Data` or `@EqualsAndHashCode` on entities (see §3 persistence).
- **Jackson 3**: `tools.jackson.databind.ObjectMapper` (not `com.fasterxml.jackson`).

## Naming

- Packages & modules: lowercase singular nouns (`identity`, `ledger`, `payment`).
- Classes: `PascalCase`; methods/fields: `camelCase`; constants: `SCREAMING_SNAKE`.
- DTOs in `api`: immutable `record`s. Request records in `internal/delivery/http/req`.
- Controllers: `<Resource>Controller` (`IdentityController`).
- Facades: `<Module>Api` interface in `api`, implementation `<Module>ApiImpl`
  (or a service) in `internal`.
- Module error enums: `<Module>Error` implementing `ErrorCode`.

## Identifiers

- **Internal / DB primary keys**: `BIGINT`, `@GeneratedValue` (DB-generated,
  `IDENTITY`/sequence). Never expose these.
- **User-facing IDs**: **UUID v7** via `UuidCreator.getTimeOrderedEpoch()`
  (time-ordered, index-friendly). Never `UUID.randomUUID()` (v4), never expose
  internal BIGINTs to clients.

## Enums

- Enums are **application-only**, persisted as `varchar` (`@Enumerated(EnumType.STRING)`
  or a String field). Never a PostgreSQL `ENUM` type.
- When an enum crosses a module boundary (api), keep it a plain Java enum; the
  internal entity may have its own mapping (see `identity`'s api `Role` vs
  internal `Role` entity).

## Records vs entities

- Records: DTOs, request/response payloads, event payloads, cache values.
- Entities: JPA classes with a no-args constructor; not records.

## Comments & Javadoc

- Javadoc is required on public `api` contracts (facades, DTOs, events) and on
  any non-obvious business rule.
- Prefer code that reads clearly over comments; remove dead/commented-out code.
- Keep references to this document stable: `see §N of AGENTS.md`.

## Miscellaneous rules

- No `System.out`/`System.err` — use `@Slf4j`.
- No mutable static state (except constant holders).
- Prefer constructor injection (`@RequiredArgsConstructor`) over field `@Autowired`.
- Strings visible to users are **never** hard-coded — resolve via the message
  source (see §6).
