# §9 — How to add a new module

Follow this checklist exactly. The `identity` and `ledger` modules are the
reference implementations.

1. **Create the package** `com.gepe.gepay.<module>` with a root `package-info.java`:

   ```java
   @ApplicationModule(id = "<module>")
   package com.gepe.gepay.<module>;

   import org.springframework.modulith.ApplicationModule;
   ```

2. **Create `api/`** with `package-info.java`:

   ```java
   @NamedInterface("api")
   package com.gepe.gepay.<module>.api;

   import org.springframework.modulith.NamedInterface;
   ```

   Add the `<Module>Api` facade interface + immutable DTO `record`s. No Spring/JPA/Web
   types in `api`.

3. **Create `internal/`** with the standard sub-packages (see §2.2): `config`,
   `entity`, `repository`, `service`, `exception`, `delivery/http`, plus `mapper`
   and `listener` if needed.

4. **Error enum** — `<Module>Error implements ErrorCode` in `internal.exception`,
   with snake_case keys prefixed `<module>.`.

5. **i18n bundles** — `i18n/<module>/messages.properties` **and**
   `messages_id.properties`, keys prefixed `<module>.`.

6. **Declare outgoing dependencies** — if the module calls another feature
   module, add `allowedDependencies = "<other>::api"` to its root
   `@ApplicationModule` (only when actually used; see §2.3).

7. **Persistence** — entities in `internal.entity`, repositories in
   `internal.repository`, and a Flyway migration `V<N>__<module>_tables.sql`.

8. **Tests** — unit tests for services; keep `ModularityTests` green.

9. **Update docs** — add the module to the tables in §1 and §2 (AGENTS.md index +
   `docs/agents/snapshot.md`).

Run `./mvnw test`; `verifyModularity` must pass.
