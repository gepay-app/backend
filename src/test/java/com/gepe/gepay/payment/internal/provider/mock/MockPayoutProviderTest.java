package com.gepe.gepay.payment.internal.provider.mock;

import com.gepe.gepay.payment.api.enums.PayoutStatus;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementRequest;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockPayoutProviderTest {

    private final MockPayoutProvider provider = new MockPayoutProvider();

    @Test
    void reportsMockCode() {
        assertThat(provider.code()).isEqualTo("MOCK");
    }

    @Test
    void disburseCompletesImmediately() {
        DisbursementResult result = provider.disburse(new DisbursementRequest(
                "PAYOUT:x", 87_000L, "BCA", "1234567890", "Budi", "remark", Map.of()));

        assertThat(result.status()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(result.providerReferenceId()).startsWith("MOCK-");
    }
}
