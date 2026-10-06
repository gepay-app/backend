# §3 — HTTP & API layer

## Routing & controllers

- Base path: `/api/v1` (`@RequestMapping("/api/v1/...")`). Resources are plural
  nouns (`/identities`, `/payments`, `/webhooks`).
- Controllers are `@RestController` in `<module>.internal.delivery.http`, thin:
  validate input, map request → command/DTO, call the `api` facade, wrap the
  result in `ApiResponse`. No business logic in controllers.
- Request bodies are `record`s in `internal/delivery/http/req` with
  `jakarta.validation` constraints (`@NotBlank`, `@Email`, `@NotNull`, `@Size`).
- Method-level authorization via `@PreAuthorize("hasRole('SUPER_ADMIN')")`
  (method security enabled in `identity`'s `SecurityConfig`).

## Response envelope (success)

```java
ApiResponse<T>(String message, T data)
```

- `message` is a localized success message (often `common.success` via
  `MessageHelper`); `data` is the payload or `null`. Null fields are omitted.

## Status codes

- Use `ResponseEntity.status(...)` explicitly; match semantics, not convenience.
- `200` for reads and in-place mutations, `201` for creation where a new
  resource identity is returned, `204` for no-content deletes. Errors follow the
  `GlobalExceptionHandler` mapping (see §6).

## Validation

- Bean validation on request records produces per-field `errors` in the
  `ErrorResponse` (400). Service-layer field problems use `ValidationException`.
- Reuse the shared constraint messages (`jakarta.validation.constraints.*`)
  already localized in the app-wide bundle.

## Pagination

- Prefer **cursor-based** pagination for high-volume lists (avoid `OFFSET`);
  document the cursor encoding. Use `Pageable` only where offset semantics are
  acceptable. (Pattern to be applied as endpoints are added — see §10.)
