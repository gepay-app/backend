package com.gepe.starter;

import com.gepe.starter.platform.security.TestFirebaseAuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestFirebaseAuthConfig.class)
class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
