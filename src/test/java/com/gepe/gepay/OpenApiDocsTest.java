package com.gepe.gepay;

import com.gepe.gepay.platform.security.TestFirebaseAuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the generated OpenAPI contract: the spec must be served at
 * {@code /v3/api-docs} with the expected metadata, known paths and the bearer
 * security scheme. Keeps the frontend-facing contract from silently breaking.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestFirebaseAuthConfig.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesOpenApiSpecWithKnownPathsAndSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("GePay API"))
                .andExpect(jsonPath("$.paths['/api/v1/balance']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/donations']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/donation-pages/{slug}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/channels']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }
}
