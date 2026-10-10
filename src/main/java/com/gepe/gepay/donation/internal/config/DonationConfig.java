package com.gepe.gepay.donation.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DonationOverlayProperties.class)
public class DonationConfig {
}
