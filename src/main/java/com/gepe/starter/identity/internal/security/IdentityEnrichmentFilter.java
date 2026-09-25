package com.gepe.starter.identity.internal.security;

import com.gepe.starter.identity.api.IdentityApi;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserStatus;
import com.gepe.starter.identity.internal.exception.IdentityError;
import com.gepe.starter.platform.security.FirebasePrincipal;
import com.gepe.starter.platform.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class IdentityEnrichmentFilter extends OncePerRequestFilter {
    private final IdentityApi identityApi;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // dapetin auth context yang sudah di verify FirebaseAuthenticationFilter
        var auth = SecurityContextHolder.getContext().getAuthentication();

        // ubah Firebase principal menjadi user principal aplikasi
        if (auth != null && auth.getPrincipal() instanceof FirebasePrincipal fp) {
            UserPrincipal userPrincipal = identityApi.resolveByAuthId(fp.authId());

            // null = belum pernah link → coba auto-provision (email harus verified)
            if (userPrincipal == null) {
                userPrincipal = identityApi.provisionOnFirstLogin(
                        fp.authId(), fp.email(), fp.name(), fp.emailVerified());
            }

            // Akun dinonaktifkan → 403 dengan alasan jelas (bukan 401 generik),
            // dan jangan lanjutkan ke controller.
            if (userPrincipal != null && userPrincipal.status() == UserStatus.DISABLED) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, IdentityError.USER_DISABLED);
                return;
            }

            if (userPrincipal != null) {
                List<GrantedAuthority> authorities = userPrincipal.roles().stream()
                        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
                        .distinct()
                        .toList();

                // set context ini jadi authenticated
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities));
            } else {
                // null = email tak terverifikasi / tidak dikenal → anonim (401 di endpoint privat)
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
