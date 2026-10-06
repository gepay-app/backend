# §6 — i18n & error handling

Single source of truth for localized text and error codes. `platform` owns the
mechanism; modules own their keys.

## Message bundles

- One `messages.properties` (English, default) + `messages_id.properties`
  (Indonesian) per module, under `i18n/<module>/`. The app-wide bundle is
  `i18n/messages/`.
- Keys are **snake_case**, prefixed by owner: `identity.user_not_found`,
  `ledger.unbalanced_journal`, `common.success`, `validation.failed`,
  `http.not_found`.
- `I18nConfig` scans `i18n/*/messages.properties` and registers one aggregated
  `MessageSource` (bean named `messageSource`) — adding a module bundle needs no
  config change.
- Locale comes from `Accept-Language`; default is English; supported locales are
  `en` and `id` (`WebConfig`). Unknown locales fall back to English, never the
  system locale.
- **Every module that adds `en` must add `id`** (and vice-versa): keep bundles in
  lockstep. A key missing in `id` falls back to English at runtime.

## Error codes

Every application error is one `ErrorCode` implementation (an enum):

```java
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getMessageKey();
}
```

- Cross-cutting codes → `platform.exception.GlobalError` (db conflicts, system
  failures, generic http/validation). Domain-specific codes → the module's own
  `<Module>Error` enum (`identity.internal.exception.IdentityError`,
  `ledger.internal.exception.LedgerError`). Never add domain codes to `GlobalError`.

## Throwing

- Business errors: `throw new ServiceException(IdentityError.USER_NOT_FOUND, email);`
  — status and message key come from the code, args are message placeholders.
- Field-level validation from the service: `throw new ValidationException(errors);`
  (400, per-field `errors`).

## Response shape (failure)

`ErrorResponse(code, message, errors)` produced by the single
`GlobalExceptionHandler`:

- `code` — **stable machine-readable identifier = the resolved message key**
  (e.g. `identity.user_not_found`). Frontends branch on `code`, never on `message`.
  Renaming a key is a breaking change.
- `message` — localized human-readable headline (rendered to users).
- `errors` — per-field problems, present only for validation (400).

Success uses `ApiResponse(message, data)`.

## Handler mapping (summary)

| Exception | HTTP | code |
|---|---|---|
| `BindException` / `HandlerMethodValidationException` / `ConstraintViolationException` | 400 | `validation.failed` (+ field `errors`) |
| `ValidationException` | 400 | `validation.failed` (+ field `errors`) |
| `HttpMessageNotReadableException` | 400 | `http.bad_request` or per-field hint |
| `MethodArgumentTypeMismatchException` / missing param | 400 | `http.invalid_param_value` / `http.parameter_required` |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `http.not_found` |
| `HttpRequestMethodNotSupportedException` | 405 | `http.method_not_allowed` |
| `HttpMediaTypeNotSupported/NotAcceptable` | 415/406 | `http.unsupported_media_type` / `http.not_acceptable` |
| `MaxUploadSizeExceededException` | 413 | `file.too_large` |
| `DataIntegrityViolationException` (duplicate key) | 409 | `db.duplicate_entry` |
| `DataIntegrityViolationException` (other) | 400 | `db.data_integrity` |
| `OptimisticLockingFailureException` | 409 | `exception.optimistic_lock` |
| `ServiceException` | from `ErrorCode` | `ErrorCode.getMessageKey()` |
| `AuthorizationDeniedException` | 403 | `http.forbidden` |
| anything else | 500 | `system.error` |

Security-filter failures (auth entry point, access denied) bypass the controller
advice and are written by `platform.security.SecurityErrorWriter` using the same
envelope.
