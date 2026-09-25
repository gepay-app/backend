package com.gepe.starter.platform.security;

import com.google.firebase.auth.FirebaseAuth;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Menggantikan {@link FirebaseConfig} (yang di-exclude di profil {@code test})
 * dengan {@link FirebaseAuth} mock, supaya test context-load tidak butuh
 * kredensial Firebase asli. Token verification tidak diuji di sini.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestFirebaseAuthConfig {

    @Bean
    FirebaseAuth firebaseAuth() {
        return Mockito.mock(FirebaseAuth.class);
    }
}
