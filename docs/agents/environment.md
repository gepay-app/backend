# §5 — Environment & infrastructure

## Configuration model

- `application.yaml` contains **only placeholders**, e.g.
  `url: ${SPRING_DATASOURCE_URL}`. **No default values are baked into the
  main config** (deliberately): every environment injects its own values via
  environment variables / `.env`.
- Every value is overridable via `SPRING_*` env vars (Spring relaxed binding).
- Local development values live in a **git-ignored `.env`** (see `.env.example`).
- `application-test.yaml` (test resources) supplies localhost/root defaults so
  context-load tests run against local Postgres/Redis without `.env`.

## Secrets

- **Never commit secrets.** No credentials, service-account JSON, or keys in the
  repo. `.env`, `*.key`, `*.pem`, `service-account.json` are git-ignored.
- Firebase credentials are injected as base64 via `FIREBASE_CREDENTIALS_BASE64`.

## Local run

Postgres and Redis must be running locally (user-managed; no compose file is
part of the repo). Then:

```bash
./mvnw spring-boot:run
```

## Profiles

- No profile is hard-coded. Optional additive profiles exist (e.g. logback `json`
  profile via `SPRING_PROFILES_ACTIVE=json` — see §7).
- `test` profile is picked up automatically by `./mvnw test`.
