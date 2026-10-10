package com.gepe.gepay.payment.internal.provider.flip;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Mendaftarkan {@link FlipProperties} sebagai bean dan membangun {@link RestClient}
 * untuk API Flip. Autentikasi memakai Basic auth dengan {@code secretKey} sebagai
 * username (sesuai konvensi API Flip).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FlipProperties.class)
public class FlipConfig {

    @Bean
    RestClient flipRestClient(FlipProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeaders(h -> {
                    h.setBasicAuth(properties.secretKey(), "");
                    h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }
}
