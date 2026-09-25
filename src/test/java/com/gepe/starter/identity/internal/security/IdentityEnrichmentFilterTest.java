package com.gepe.starter.identity.internal.security;

import com.gepe.starter.identity.api.IdentityApi;
import com.gepe.starter.identity.api.dtos.Role;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserStatus;
import com.gepe.starter.platform.i18n.I18nConfig;
import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.security.FirebasePrincipal;
import com.gepe.starter.platform.security.SecurityErrorWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test filter enrichment (tanpa Spring context): memastikan akun DISABLED
 * dibalas 403 dengan code jelas, dan bukan 401 generik.
 */
class IdentityEnrichmentFilterTest {

    private IdentityApi identityApi;
    private IdentityEnrichmentFilter filter;

    @BeforeEach
    void setUp() throws IOException {
        identityApi = mock(IdentityApi.class);
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(
                JsonMapper.builder().build(),
                new MessageHelper(new I18nConfig().messageSource()));
        filter = new IdentityEnrichmentFilter(identityApi, errorWriter);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns403WithClearCodeWhenUserDisabled() throws Exception {
        String authId = "uid-disabled";
        when(identityApi.resolveByAuthId(authId)).thenReturn(new UserPrincipal(
                UUID.randomUUID(), "a@example.com", null, Set.of(Role.USER), UserStatus.DISABLED));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/identities/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        authenticate(authId);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"identity.user.disabled\"");
        assertThat(chain.getRequest()).isNull(); // chain tidak dilanjutkan
    }

    @Test
    void setsPrincipalAndContinuesForActiveUser() throws Exception {
        String authId = "uid-active";
        when(identityApi.resolveByAuthId(authId)).thenReturn(new UserPrincipal(
                UUID.randomUUID(), "a@example.com", null, Set.of(Role.USER), UserStatus.ACTIVE));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/identities/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        authenticate(authId);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserPrincipal.class);
    }

    private void authenticate(String authId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new FirebasePrincipal(authId, "a@example.com", null, true), null, List.of()));
    }
}
