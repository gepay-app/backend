package com.gepe.gepay.platform.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated spec (springdoc) — the machine-readable
 * contract the frontend consumes.
 *
 * <p>Every response is wrapped in {@code ApiResponse<T>} (success) or
 * {@code ErrorResponse} (failure); see AGENTS.md §6. Protected endpoints expect a
 * Firebase ID token as {@code Authorization: Bearer <token>} — wired here so the
 * generated spec / Swagger UI can send it.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "GePay API",
        version = "v1",
        description = """
                Modular-monolith backend (payment, ledger, donation, identity).

                Conventions:
                - Success body: `{ "message": string|null, "data": T }` (`ApiResponse<T>`).
                - Error body: `{ "code": string, "message": string, "errors": [...]? }` (`ErrorResponse`).
                - `code` is a stable i18n key (e.g. `payment.channel_not_found`).
                - Lists are cursor-paginated: `data = { items, hasNext, nextCursor }` (no total count). Pass `?cursor=<nextCursor>` to fetch the next page; `size` defaults to 20 and is capped at 100.
                - Authenticated endpoints need `Authorization: Bearer <Firebase ID token>`.
                """))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "Firebase ID token")
public class OpenApiConfig {
}
