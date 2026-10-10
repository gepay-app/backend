package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.DonationPageService;
import com.gepe.gepay.identity.api.IdentityApi;
import com.gepe.gepay.identity.api.dtos.UserPrincipal;
import com.gepe.gepay.identity.api.dtos.UserStatus;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolusi identitas saat handshake:
 * <ul>
 *   <li><b>display</b> ({@code /ws/overlay/display?key=…}) — TANPA login; key
 *       rahasia dipetakan ke {@code creatorId}. Key tidak dikenal → tolak.</li>
 *   <li><b>control</b> ({@code /ws/overlay/control?token=…}) — verifikasi Firebase
 *       ID token, lalu petakan ke user; token invalid/disabled → tolak.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlayHandshakeInterceptor implements HandshakeInterceptor {

    private final DonationPageService donationPageService;
    private final FirebaseAuth firebaseAuth;
    private final IdentityApi identityApi;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        Map<String, String> query = queryParams(request);

        if (path.endsWith("/display")) {
            return resolveDisplay(query, response, attributes);
        }
        if (path.endsWith("/control")) {
            return resolveControl(query, response, attributes);
        }
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private boolean resolveDisplay(Map<String, String> query, ServerHttpResponse response, Map<String, Object> attributes) {
        String key = query.get("key");
        if (key == null || key.isBlank()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        Optional<UUID> creatorId = donationPageService.findCreatorIdByOverlayKey(key);
        if (creatorId.isEmpty()) {
            log.debug("Rejected overlay display handshake: unknown overlay key");
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        attributes.put(OverlayWsSupport.ATTR_CREATOR_ID, creatorId.get());
        attributes.put(OverlayWsSupport.ATTR_ROLE, OverlayWsSupport.ROLE_DISPLAY);
        return true;
    }

    private boolean resolveControl(Map<String, String> query, ServerHttpResponse response, Map<String, Object> attributes) {
        String token = query.get("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            String authId = firebaseAuth.verifyIdToken(token).getUid();
            UserPrincipal principal = identityApi.resolveByAuthId(authId);
            if (principal == null || principal.status() != UserStatus.ACTIVE) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(OverlayWsSupport.ATTR_CREATOR_ID, principal.userId());
            attributes.put(OverlayWsSupport.ATTR_ROLE, OverlayWsSupport.ROLE_CONTROL);
            return true;
        } catch (Exception e) {
            log.debug("Rejected overlay control handshake: invalid token ({})", e.getClass().getSimpleName());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    private static Map<String, String> queryParams(ServerHttpRequest request) {
        var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        return params.toSingleValueMap();
    }
}
