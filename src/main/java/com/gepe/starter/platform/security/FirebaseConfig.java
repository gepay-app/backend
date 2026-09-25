package com.gepe.starter.platform.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Konfigurasi Firebase Admin SDK. Di-exclude pada profil {@code test} supaya
 * test context-load tidak butuh kredensial asli — {@code TestFirebaseAuthConfig}
 * yang menyediakan {@link FirebaseAuth} mock.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class FirebaseConfig{

    @Bean
    FirebaseApp firebaseApp(@Value("${firebase.credentials-base64}") String credentialsBase64) throws IOException {
        if(!FirebaseApp.getApps().isEmpty()){
            return FirebaseApp.getInstance();
        }

        if(!StringUtils.hasText(credentialsBase64)){
            throw new IllegalStateException("FIREBASE_CREDENTIALS_BASE64 is not set");
        }

        byte[] decoded = Base64.getDecoder().decode(credentialsBase64.getBytes(StandardCharsets.UTF_8));
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(decoded)))
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
