package com.gepe.gepay.payment.internal.provider.flip;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.PayoutStatus;
import com.gepe.gepay.payment.api.enums.ProcessedEventType;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayoutProvider;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementRequest;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.provider.dtos.ProviderStatus;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Adapter payout ke Flip (sandbox). Hanya menembak API vendor: menerjemahkan
 * {@link DisbursementRequest} netral menjadi request form-urlencoded Flip dan
 * memetakan balik status vendor ke {@link PayoutStatus}. Fee dihitung service
 * payment, bukan di sini.
 *
 * <p>Endpoint disbursement: {@code POST /v3/disbursement} dengan body
 * {@code account_number, bank_code, amount, remark, beneficiary_email}
 * (form-urlencoded). Autentikasi Basic memakai {@code secret_key}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlipPayoutClient implements PayoutProvider {

    private final RestClient flipRestClient;
    private final ObjectMapper objectMapper;
    private final FlipProperties properties;

    @Override
    public String code() {
        return "FLIP";
    }

    @Override
    public DisbursementResult disburse(DisbursementRequest request) {
        Map<String, Object> rawRequest = new LinkedHashMap<>();
        rawRequest.put("account_number", request.accountNumber());
        rawRequest.put("bank_code", request.providerChannelCode());
        rawRequest.put("amount", request.amount());
        rawRequest.put("remark", request.remark());
        if (request.metadata() != null && request.metadata().get("beneficiary_email") != null) {
            rawRequest.put("beneficiary_email", request.metadata().get("beneficiary_email"));
        }

        try {
            return flipRestClient.post()
                    .uri("/v3/disbursement")
                    .header("idempotency-key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toForm(rawRequest))
                    .exchange((req, res) -> {
                        String rawResponseJson = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (res.getStatusCode().isError()) {
                            log.error("Flip disbursement HTTP error: status={}, body={}",
                                    res.getStatusCode().value(), rawResponseJson);
                            throw new IllegalStateException("Flip disbursement error: " + rawResponseJson);
                        }
                        Map<String, Object> rawResponse = objectMapper.readValue(rawResponseJson, new TypeReference<>() {
                        });
                        FlipDisbursementResponse parsed = objectMapper.convertValue(rawResponse, FlipDisbursementResponse.class);
                        if (parsed == null || parsed.id() == null) {
                            throw new IllegalStateException("Flip returned response without disbursement id: " + rawResponseJson);
                        }
                        return new DisbursementResult(
                                String.valueOf(parsed.id()),
                                mapStatus(parsed.status()),
                                rawRequest,
                                rawResponse
                        );
                    });
        } catch (RestClientException e) {
            log.error("Flip disbursement communication failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Flip disbursement response", e);
            throw new IllegalStateException("Failed to process Flip disbursement response", e);
        }
    }

    @Override
    public ProviderStatus getStatus(String providerReferenceId) {
        try {
            return flipRestClient.get()
                    .uri("/v3/disbursement/{id}", providerReferenceId)
                    .exchange((req, res) -> {
                        String rawResponseJson = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (res.getStatusCode().isError()) {
                            log.error("Flip get-disbursement HTTP error: status={}, body={}",
                                    res.getStatusCode().value(), rawResponseJson);
                            throw new IllegalStateException("Flip get-disbursement error: " + rawResponseJson);
                        }
                        Map<String, Object> rawResponse = objectMapper.readValue(rawResponseJson, new TypeReference<>() {
                        });
                        FlipDisbursementResponse parsed = objectMapper.convertValue(rawResponse, FlipDisbursementResponse.class);
                        if (parsed == null) {
                            throw new IllegalStateException("Flip returned empty disbursement response");
                        }
                        return new ProviderStatus(
                                providerReferenceId,
                                mapAttemptStatus(parsed.status()),
                                null,
                                rawResponse
                        );
                    });
        } catch (RestClientException e) {
            log.error("Flip get-disbursement communication failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Flip get-disbursement response", e);
            throw new IllegalStateException("Failed to process Flip get-disbursement response", e);
        }
    }

    @Override
    public IncomingProviderNotification parseWebhook(String rawBody, Map<String, String> headers) {
        String token = headers.getOrDefault("token", "");
        if (!MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.validationKey().getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException(PaymentError.INVALID_FLIP_WEBHOOK);
        }

        try {
            Map<String, Object> raw = objectMapper.readValue(rawBody, new TypeReference<>() {
            });
            String id = raw.get("id") == null ? null : String.valueOf(raw.get("id"));
            String status = raw.get("status") == null ? null : String.valueOf(raw.get("status"));

            PayoutStatus payoutStatus = mapStatus(status);
            ProcessedEventType eventType = payoutStatus == PayoutStatus.COMPLETED
                    ? ProcessedEventType.PAYOUT_COMPLETED
                    : payoutStatus == PayoutStatus.FAILED || payoutStatus == PayoutStatus.REVERSED
                    ? ProcessedEventType.PAYOUT_FAILED
                    : null;

            return new IncomingProviderNotification(
                    eventType,
                    id,
                    mapAttemptStatus(status),
                    null,
                    Instant.now(),
                    raw
            );
        } catch (Exception e) {
            log.error("Failed to parse Flip webhook body", e);
            throw new IllegalArgumentException("Invalid Flip webhook notification", e);
        }
    }

    private PayoutStatus mapStatus(String status) {
        if (status == null) {
            return PayoutStatus.PENDING;
        }
        return switch (status.toUpperCase()) {
            case "DONE" -> PayoutStatus.COMPLETED;
            case "REVERSED" -> PayoutStatus.REVERSED;
            case "FAILED", "CANCELLED" -> PayoutStatus.FAILED;
            default -> PayoutStatus.PENDING;
        };
    }

    private PaymentAttemptStatus mapAttemptStatus(String status) {
        return switch (mapStatus(status)) {
            case COMPLETED -> PaymentAttemptStatus.PAID;
            case FAILED, REVERSED -> PaymentAttemptStatus.FAILED;
            case PENDING -> PaymentAttemptStatus.PENDING;
        };
    }

    /** Bangun body form-urlencoded (Flip memakai format ini, bukan JSON). */
    private String toForm(Map<String, Object> fields) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    /** Response disbursement Flip (id = referensi unik sisi Flip). */
    public record FlipDisbursementResponse(Long id, String status, Long fee) {
    }
}
