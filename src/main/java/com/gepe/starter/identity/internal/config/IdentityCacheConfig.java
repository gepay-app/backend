package com.gepe.starter.identity.internal.config;


import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.platform.config.CacheSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class IdentityCacheConfig {
    public static final String PRINCIPAL_BY_AUTH_ID = "identity-principal-by-auth-id";

    @Bean
    CacheSpec principalByAuthIdCacheSpec(){
        return CacheSpec.single(PRINCIPAL_BY_AUTH_ID, Duration.ofMinutes(10), UserPrincipal.class);
    }
}
