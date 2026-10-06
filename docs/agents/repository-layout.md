# §4 — Repository & resources layout

## Source roots

```
src/main/java/com/gepe/gepay/   # application + modules (see §2)
src/main/resources/
├── application.yaml            # env-var placeholders, no secrets, no hard-coded profiles
├── db/migration/               # Flyway V<N>__*.sql
├── i18n/                       # app-wide + per-module message bundles
│   ├── messages/               # app-wide (common, http, db, validation, system, …)
│   ├── identity/               # identity module bundle
│   └── ledger/                 # ledger module bundle
└── logback-spring.xml          # logging config + profiles
src/test/java/                  # tests mirror main packages
src/test/resources/             # application-test.yaml, test i18n fixtures
```

## Docs & specs

- `docs/agents/*.md` — this documentation set (normative conventions).
- `readme/` — per-module design/implementation notes (may lag code; treat as
  reference, not authority).
- `specs/` — feature specifications/design scratch.
- `todos/` — work checklists.

## What must NOT live in the repo

- Secrets, credentials, service-account JSON, private keys (`.env` and such are
  git-ignored; see §5).
- Generated build output (`target/`).
