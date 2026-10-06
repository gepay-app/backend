package com.gepe.gepay.identity.internal.service;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.identity.api.dtos.UserPrincipal;
import com.gepe.gepay.platform.exception.GlobalError;
import com.gepe.gepay.platform.exception.ServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation of the {@link CurrentUser} contract: reads the authenticated
 * {@link UserPrincipal} from the Spring Security context (set by
 * {@code IdentityEnrichmentFilter}). Anonymous access yields a 401.
 */
@Component
public class CurrentUserImpl implements CurrentUser {

    @Override
    public UserPrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new ServiceException(GlobalError.HTTP_UNAUTHORIZED);
        }
        return p;
    }

    @Override
    public UUID userId() {
        return get().userId();
    }
}
