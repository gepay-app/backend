package com.gepe.gepay.payment.internal.provider.midtrans;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Mendaftarkan {@link MidtransProperties} sebagai bean agar di-bind dari
 * konfigurasi {@code pg.midtrans.*}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MidtransProperties.class)
public class MidtransConfig {

    @Bean
    RestClient midtransRestClient(MidtransProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeaders(h -> {
                    h.setBasicAuth(properties.serverKey(), "");
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }
}
