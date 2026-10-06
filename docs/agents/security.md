# §12 — Security & authentication

## Auth pipeline

1. **`FirebaseAuthenticationFilter`** (`platform.security`) verifies the
   `Authorization: Bearer <id-token>` with Firebase, and puts a platform-level
   `FirebasePrincipal(authId, email, name, emailVerified)` in the SecurityContext.
   `platform` never knows about `identity` here.
2. **`IdentityEnrichmentFilter`** (`identity.internal.security`) converts the
   `FirebasePrincipal` into the app-level `identity.api.dtos.UserPrincipal`
   (resolve or auto-provision via `IdentityApi`), sets `ROLE_*` authorities, and
   rejects `DISABLED` users with a 403 via `SecurityErrorWriter`.
3. **`SecurityConfig`** (`identity.internal.config`) wires the filter chain,
   enables method security, and defines the CORS policy. The filter chain is
   `STATELESS`.

## Reading the current user

- `identity.api.CurrentUser` is the **interface**; its `@Component` implementation
  lives in `identity.internal.service.CurrentUserImpl`. Inject the interface from
  any module:

  ```java
  UUID actorId = currentUser.userId();       // for ownership checks
  UserPrincipal principal = currentUser.get(); // rich principal (roles, status)
  ```

  Anonymous access throws 401 (`GlobalError.HTTP_UNAUTHORIZED`).

## Authorization

- Method-level: `@PreAuthorize("hasRole('SUPER_ADMIN')")` on controller methods.
- Resource-level (ownership): check `currentUser.userId()` against the resource's
  owner inside the service.
- Role authorities are prefixed `ROLE_` (e.g. `ROLE_SUPER_ADMIN`).

## Public endpoints & webhooks

- Only `/actuator/health/**` is `permitAll` by default. Webhook endpoints
  (`/api/v1/webhooks/**`) must be `permitAll` **but** verify the provider's
  signature/callback secret — never trust the payload. (Applied as `payment`
  webhooks land.)

## Error writer

- Security-filter failures don't reach `GlobalExceptionHandler`; they are written
  by `platform.security.SecurityErrorWriter` using the **same** `ErrorResponse`
  envelope (see §6).
