# §2 — Module layout & the `api`/`internal` split

The application is a **modular monolith**: one deployable Spring Boot app whose
packages are organized into *application modules* enforced by Spring Modulith.
The goal is compile-time + test-time guarantees about what code may touch what,
while still deploying as a single process.

## §2.1 Golden rules (non-negotiable)

1. A feature module exposes **only its `api` package**. Everything under
   `internal` is private to that module.
2. Other modules may use a feature module **only** through its `api`, and **only**
   when the dependency is declared via `allowedDependencies = "<module>::api"`.
3. `api` is a **pure contract**: interfaces, immutable DTO `record`s, event
   `record`s. It must **never** depend on `internal`, Spring Data/JPA types, or
   Web/HTTP types. (The one deliberate exception is documented in §12: `CurrentUser`
   is an interface in `identity.api`, its Spring implementation lives in `internal`.)
4. Dependencies point **inward**: `internal` may use `api` (its own), `api` may
   not use `internal`; a module may not reach into another module's `internal`.

## §2.2 Standard layout

```
com.gepe.gepay.<module>/
├── package-info.java              # @ApplicationModule(...)
├── api/                           # PUBLIC contract (the only exported package)
│   ├── package-info.java          # @NamedInterface("api")
│   ├── <Module>Api.java           # facade interface (implements in internal)
│   ├── dtos/                      # immutable records (request/response/event payloads)
│   └── enums/                     # shared enums (plain Java, no JPA)
└── internal/
    ├── config/                    # module @Configuration + CacheSpec beans
    ├── delivery/http/             # @RestController (thin; delegates to api facade)
    │   └── req/                   # request records (bean-validation constraints)
    ├── entity/                    # JPA entities (module-private)
    ├── repository/                # Spring Data repositories (module-private)
    ├── service/                   # facade impl + business services
    ├── exception/                 # <Module>Error enum (implements ErrorCode)
    ├── mapper/                    # entity -> DTO mappers (if needed)
    ├── security/                  # module-specific security components (if any)
    └── listener/                  # event listeners (if any)
```

## §2.3 Module annotation

**Root `package-info.java`** declares the module:

```java
@ApplicationModule(id = "payment", allowedDependencies = "ledger::api")
package com.gepe.gepay.payment;

import org.springframework.modulith.ApplicationModule;
```

**`api/package-info.java`** marks the exported named interface:

```java
@NamedInterface("api")
package com.gepe.gepay.payment.api;

import org.springframework.modulith.NamedInterface;
```

### How `allowedDependencies` actually works (read this before touching it)

- `allowedDependencies` constrains **cross-module** access **only**. It has no
  effect *inside* a module: any package in the same module may freely reference
  any other package of that module (`internal.service` → `internal.repository`,
  `internal.entity`, and its own `api`) — this is never a violation.
- A **CLOSED** module may only be reached through its named interface (`api`),
  and only by a module that declares `allowedDependencies = "<module>::api"`.
  Without the declaration, `ApplicationModules.verify()` fails.
- A declaration that is not yet backed by an actual `import` is harmless (it
  merely grants permission), but keep it truthful: declare the dependency when
  the code actually calls the other module, not speculatively.
- The qualifier must name the **named interface**: `"ledger::api"`. A bare
  `"ledger"` does not grant access to `ledger.api` for a CLOSED module.

## §2.4 The `platform` module (shared)

`platform` is the shared infrastructure module, declared `OPEN`:

```java
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.gepe.gepay.platform;
```

and registered as shared in the application class:

```java
@Modulith(sharedModules = "platform")
public class Application { ... }
```

Rules:

- `platform` may be used by **every** feature module without listing it in
  `allowedDependencies` (it is `shared`).
- `platform` must **never** depend on a feature module. This is why, for
  example, the raw Firebase verification (`FirebasePrincipal`) lives in
  `platform.security` while the app-level `UserPrincipal` and enrichment live
  in `identity`.
- `platform` sub-packages (current): `config/` (cache infra), `exception/`,
  `i18n/`, `logging/`, `modulith/` (event-publication recovery), `security/`,
  `web/` (envelope, exception handler, filters).

### Nested modules (warning)

Only annotate a **root** module package with `@ApplicationModule`. Annotating a
sub-package creates a *nested module*, which re-introduces access restrictions
between what was one module. Keep the root-only rule unless a nested boundary is
deliberately intended (and documented).
