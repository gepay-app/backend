package com.gepe.gepay.donation.internal.config;

import com.gepe.gepay.donation.internal.ws.OverlayControlHandler;
import com.gepe.gepay.donation.internal.ws.OverlayDisplayHandler;
import com.gepe.gepay.donation.internal.ws.OverlayHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registrasi handler WebSocket overlay:
 * <ul>
 *   <li>{@code /ws/overlay/display} — OBS, pasif (resolusi {@code overlay_key});</li>
 *   <li>{@code /ws/overlay/control} — owner, login Firebase.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@RequiredArgsConstructor
public class OverlayWebSocketConfig implements WebSocketConfigurer {

    private final OverlayDisplayHandler displayHandler;
    private final OverlayControlHandler controlHandler;
    private final OverlayHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(displayHandler, "/ws/overlay/display")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
        registry.addHandler(controlHandler, "/ws/overlay/control")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
