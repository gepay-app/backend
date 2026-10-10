package com.gepe.gepay.payment.internal.provider.mock;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.PayoutStatus;
import com.gepe.gepay.payment.internal.provider.PayoutProvider;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementRequest;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.provider.dtos.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Provider payout palsu untuk development: langsung mengembalikan
 * {@code COMPLETED} tanpa memanggil vendor. Dipakai bila
 * {@code payment.payout.mock-enabled=true} (mis. saat sandbox Flip bermasalah).
 *
 * <p>Alur ledger tidak berubah — payout tetap selesai & J-6 diposting.
 */
@Slf4j
@Component
public class MockPayoutProvider implements PayoutProvider {

    public static final String CODE = "MOCK";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public DisbursementResult disburse(DisbursementRequest request) {
        String reference = "MOCK-" + UUID.randomUUID();
        log.info("MOCK payout disburse idempotencyKey={}, amount={}, reference={}",
                request.idempotencyKey(), request.amount(), reference);
        return new DisbursementResult(
                reference,
                PayoutStatus.COMPLETED,
                Map.of(
                        "idempotencyKey", String.valueOf(request.idempotencyKey()),
                        "amount", request.amount(),
                        "accountNumber", String.valueOf(request.accountNumber())),
                Map.of("status", "COMPLETED", "providerReferenceId", reference));
    }

    @Override
    public ProviderStatus getStatus(String providerReferenceId) {
        return new ProviderStatus(providerReferenceId, PaymentAttemptStatus.PAID, Instant.now(),
                Map.of("status", "COMPLETED"));
    }

    @Override
    public IncomingProviderNotification parseWebhook(String rawBody, Map<String, String> headers) {
        throw new UnsupportedOperationException("MOCK payout provider has no webhook");
    }
}
