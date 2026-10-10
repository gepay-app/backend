package com.gepe.gepay.identity.internal.config;

import com.gepe.gepay.identity.api.IdentityApi;
import com.gepe.gepay.identity.internal.security.IdentityEnrichmentFilter;
import com.gepe.gepay.platform.security.FirebaseAuthenticationFilter;
import com.gepe.gepay.platform.security.RestAccessDeniedHandler;
import com.gepe.gepay.platform.security.RestAuthenticationEntryPoint;
import com.gepe.gepay.platform.security.SecurityErrorWriter;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final RestAuthenticationEntryPoint entryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityErrorWriter securityErrorWriter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, IdentityApi identityApi, FirebaseAuth firebaseAuth) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // OpenAPI spec + Swagger UI (dev/frontend tooling).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll() // buat webhook midtarans/doku/flip
                        // Katalog channel publik (donor anonim memilih metode bayar).
                        .requestMatchers(HttpMethod.GET, "/api/v1/channels").permitAll()
                        // Donation: donasi dibuat & status halaman donasi bisa diakses publik
                        // (donor anonim). Endpoint owner (`/donations/me/**`) tetap butuh auth,
                        // jadi matcher authenticated harus didahulukan sebelum wildcard `/*`.
                        .requestMatchers(HttpMethod.GET, "/api/v1/donations/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/donations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/donations/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/donation-pages/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/donations/*/stream").permitAll()
                        // WebSocket overlay: handshake di-resolve sendiri oleh
                        // OverlayHandshakeInterceptor (key display / token control).
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new FirebaseAuthenticationFilter(firebaseAuth), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new IdentityEnrichmentFilter(identityApi, securityErrorWriter), FirebaseAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (StringUtils.hasText(allowedOrigins)) {
            config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false); // Bearer token di header, bukan cookie — gak butuh credentials mode

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
