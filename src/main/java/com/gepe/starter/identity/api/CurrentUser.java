package com.gepe.starter.identity.api;

import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.platform.exception.GlobalError;
import com.gepe.starter.platform.exception.ServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Dipanggil dari service modul MANAPUN buat authz level resource (kepemilikan data, dsb). */
@Component
public class CurrentUser {
    public UserPrincipal get(){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)){
            throw new ServiceException(GlobalError.HTTP_UNAUTHORIZED);
        }
        return p;
    }

    public UUID userId() {
        return get().userId();
    }
}
