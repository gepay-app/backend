package com.gepe.gepay.identity.api;

import com.gepe.gepay.identity.api.dtos.UserPrincipal;

import java.util.UUID;

/**
 * Accessor for the authenticated application user (see §6 of {@code AGENTS.md}).
 *
 * <p>Pure contract: the implementation lives in
 * {@code identity.internal.service.CurrentUserImpl}. Inject this interface from
 * any module that needs resource-level authorization (ownership checks etc.).
 * The implementation reads the principal that
 * {@code IdentityEnrichmentFilter} stored in the Spring Security context.
 */
public interface CurrentUser {

    /** The authenticated {@link UserPrincipal}, or throws 401 when anonymous. */
    UserPrincipal get();

    /** Shortcut for {@code get().userId()}. */
    UUID userId();
}
