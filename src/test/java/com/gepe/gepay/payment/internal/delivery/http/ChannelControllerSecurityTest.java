package com.gepe.gepay.payment.internal.delivery.http;

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
 * Guards the auth boundary of the split channel catalog: PAYIN is public
 * (anonymous donors), PAYOUT requires a bearer token (fees depend on the user's
 * {@code UserFeeOverride}).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestFirebaseAuthConfig.class)
class ChannelControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void payinCatalogIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/channels/payin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void payoutCatalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/channels/payout"))
                .andExpect(status().isUnauthorized());
    }
}
