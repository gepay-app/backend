package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.DonationPageService;
import com.gepe.gepay.identity.api.IdentityApi;
import com.gepe.gepay.identity.api.dtos.Role;
import com.gepe.gepay.identity.api.dtos.UserPrincipal;
import com.gepe.gepay.identity.api.dtos.UserStatus;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverlayHandshakeInterceptorTest {

    @Mock
    private DonationPageService donationPageService;
    @Mock
    private FirebaseAuth firebaseAuth;
    @Mock
    private IdentityApi identityApi;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;

    private OverlayHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new OverlayHandshakeInterceptor(donationPageService, firebaseAuth, identityApi);
    }

    @Test
    void displayResolvesOverlayKeyToCreator() {
        UUID creatorId = UUID.randomUUID();
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/display?key=abc"));
        when(donationPageService.findCreatorIdByOverlayKey("abc")).thenReturn(Optional.of(creatorId));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(OverlayWsSupport.ATTR_CREATOR_ID)).isEqualTo(creatorId);
        assertThat(attributes.get(OverlayWsSupport.ATTR_ROLE)).isEqualTo(OverlayWsSupport.ROLE_DISPLAY);
    }

    @Test
    void displayRejectedForUnknownKey() {
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/display?key=nope"));
        when(donationPageService.findCreatorIdByOverlayKey("nope")).thenReturn(Optional.empty());

        boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
    }

    @Test
    void displayRejectedWithoutKey() {
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/display"));

        boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
    }

    @Test
    void controlResolvesTokenToPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/control?token=good"));
        FirebaseToken token = org.mockito.Mockito.mock(FirebaseToken.class);
        when(firebaseAuth.verifyIdToken("good")).thenReturn(token);
        when(token.getUid()).thenReturn("firebase-uid");
        when(identityApi.resolveByAuthId("firebase-uid")).thenReturn(
                new UserPrincipal(userId, "a@b.com", "Budi", Set.of(Role.CREATOR), UserStatus.ACTIVE));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(OverlayWsSupport.ATTR_CREATOR_ID)).isEqualTo(userId);
        assertThat(attributes.get(OverlayWsSupport.ATTR_ROLE)).isEqualTo(OverlayWsSupport.ROLE_CONTROL);
    }

    @Test
    void controlRejectedForInvalidToken() throws Exception {
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/control?token=bad"));
        when(firebaseAuth.verifyIdToken("bad")).thenThrow(new RuntimeException("bad token"));

        boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void controlRejectedWithoutToken() {
        when(request.getURI()).thenReturn(URI.create("https://x/ws/overlay/control"));

        boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
