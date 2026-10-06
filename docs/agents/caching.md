# §3 — Caching

Cache store is **shared Redis only** (never in-process), configured once in
`platform/config`. Modules declare `CacheSpec` beans; they never configure the
`CacheManager` themselves.

## Declaring a cache (module side)

In `<module>.internal.config`, expose a `CacheSpec` bean:

```java
@Bean
CacheSpec principalByAuthIdCacheSpec() {
    return CacheSpec.single("identity-principal-by-auth-id", Duration.ofMinutes(10), UserPrincipal.class);
}
```

- `CacheSpec.single(name, ttl, recordClass)` for single values;
  `CacheSpec.list(name, ttl, elementRecordClass)` for list values.
- The value/element type **must be an api DTO `record`** — enforced at startup.
- One cache name = one value type. Names are unique across the app.

## Reading & evicting

- Read paths: `@Cacheable(cacheNames = NAME, key = "...")` returning an api DTO
  record. Use `unless = "#result == null"` when null results are valid and must
  not be cached (see `identity`'s principal lookup).
- Mutating paths: `@CacheEvict(cacheNames = NAME, key = "...")`.
- Eviction/put is **transaction-aware** (`transactionAware()`): it runs after
  the surrounding transaction commits, so a rolled-back write never evicts a
  still-valid entry.
- TTL is a safety net **on top of** explicit eviction — never rely on TTL alone
  for correctness.

## Fail-fast

`@Cacheable` with a cache name that has no `CacheSpec` throws at startup
(`disableCreateOnMissingCache`) — a typo fails fast instead of silently creating
an unconfigured cache.
