package com.gepe.gepay.payment.internal.provider.midtrans;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.ProcessedEventType;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.payment.internal.provider.dtos.*;
import com.gepe.gepay.platform.exception.GlobalError;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class MidtransPayinClient implements PayinProvider {

    private final String code = "MIDTRANS";
    private final RestClient midtransRestClient;
    private final ObjectMapper objectMapper;
    private final MidtransProperties properties;

    @Override
    public String code() {
        return this.code;
    }

    @Override
    public ChargeResult createCharge(ChargeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        MidtransChargeRequest payload = generateRequestPayload(request);

        try {
            // Serialize payload menjadi JSON yang akan dikirim.
            String rawRequestJson = objectMapper.writeValueAsString(payload);

            return midtransRestClient.post()
                    .uri("/v2/charge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rawRequestJson)
                    .exchange((httpRequest, httpResponse) -> {
                        // Baca response body mentah, sebelum parsing.
                        String rawResponseJson = new String(
                                httpResponse.getBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        );

                        int status = httpResponse.getStatusCode().value();

                        // Jangan parsing response error menjadi DTO sukses.
                        if (httpResponse.getStatusCode().isError()) {
                            log.error(
                                    "Midtrans HTTP error: status={}, body={}",
                                    status,
                                    rawResponseJson
                            );

                            throw new IllegalStateException("Midtrans HTTP error: " + rawResponseJson);
                        }

                        try {
                            Map<String, Object> rawRequest =
                                    objectMapper.readValue(
                                            rawRequestJson,
                                            new TypeReference<>() {
                                            }
                                    );

                            Map<String, Object> rawResponse =
                                    objectMapper.readValue(
                                            rawResponseJson,
                                            new TypeReference<>() {
                                            }
                                    );

                            MidtransChargeResponse response =
                                    objectMapper.readValue(
                                            rawResponseJson,
                                            MidtransChargeResponse.class
                                    );

                            if (response == null) {
                                throw new IllegalStateException(
                                        "Midtrans returned an empty response"
                                );
                            }

                            ChargeResult result = toChargeResult(
                                    request,
                                    response,
                                    rawRequest,
                                    rawResponse
                            );

                            if (result.providerReferenceId() == null) {
                                throw new IllegalStateException(
                                        "Missing Midtrans transaction ID"
                                );
                            }

                            return result;

                        } catch (Exception e) {
                            // Parsing gagal. Raw body tetap tersedia di sini.
                            log.error(
                                    "Invalid Midtrans response: status={}, body={}",
                                    status,
                                    rawResponseJson,
                                    e
                            );

                            throw new IllegalArgumentException(
                                    "Failed to parse Midtrans response",
                                    e
                            );
                        }
                    });

        } catch (RestClientException e) {
            log.error("Midtrans communication failed", e);
            throw e;
        } catch (Exception e) {
            // Gagal melakukan serialisasi request.
            throw new IllegalStateException(
                    "Failed to serialize Midtrans request",
                    e
            );
        }
    }

    @Override
    public ProviderStatus getStatus(String providerReferenceId) {
        try {
            return midtransRestClient.get()
                    .uri("/v2/{transactionId}/status", providerReferenceId)
                    .exchange((req, res) -> {

                        String rawResponseJson = new String(
                                res.getBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        );

                        int status = res.getStatusCode().value();

                        if (res.getStatusCode().isError()) {
                            log.error(
                                    "Midtrans HTTP error: status={}, body={}",
                                    status,
                                    rawResponseJson
                            );

                            throw new IllegalStateException("Midtrans HTTP error: " + rawResponseJson);
                        }

                        try {
                            Map<String, Object> rawResponse = objectMapper.readValue(
                                    rawResponseJson,
                                    new TypeReference<>() {
                                    }
                            );

                            MidtransGetStatusResponse response = objectMapper.convertValue(
                                    rawResponse,
                                    MidtransGetStatusResponse.class
                            );

                            if (response == null) {
                                throw new IllegalStateException(
                                        "Midtrans returned an empty response"
                                );
                            }

                            return toProviderStatus(response, rawResponse);
                        } catch (Exception e) {
                            // Parsing gagal. Raw body tetap tersedia di sini.
                            log.error(
                                    "Invalid Midtrans response: status={}, body={}",
                                    status,
                                    rawResponseJson,
                                    e
                            );

                            throw new IllegalArgumentException(
                                    "Failed to parse Midtrans response",
                                    e
                            );
                        }


                    });


        } catch (RestClientException e) {
            log.error("Midtrans communication failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to serialize Midtrans response", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cancel(String providerReferenceId) {
        throw new ServiceException(GlobalError.SYSTEM_MAINTENANCE);
    }

    @Override
    public IncomingProviderNotification parseWebhook(String rawBody, Map<String, String> headers) {
        try {
            // parsing raw ke MidtransNotificationPayload
            MidtransNotificationPayload payload = objectMapper.readValue(rawBody, MidtransNotificationPayload.class);

            // validasi signature (private method aja), cek juga signature not null
            validateSignature(payload);

            PaymentAttemptStatus attemptStatus = mapAttemptStatus(payload.transaction_status());
            ProcessedEventType eventType = mapEventType(payload.transaction_status());

            Long amount = null;
            if (payload.gross_amount() != null) {
                amount = new BigDecimal(payload.gross_amount()).longValue(); // buletin aja, midtrans juga ga pernah ngasih decimal
            }

            // occurredAt tidak boleh null (dipakai untuk paid date & jurnal),
            // jadi kalau PG tidak mengirim waktu, jatuh ke sekarang.
            Instant occurredAt = parseTime(
                    payload.settlement_time() != null ? payload.settlement_time() : payload.transaction_time());
            if (occurredAt == null) {
                occurredAt = Instant.now();
            }
            Map<String, Object> rawMap = objectMapper.readValue(rawBody, new TypeReference<>() {});

            return new IncomingProviderNotification(
                    eventType,
                    payload.transaction_id(),
                    attemptStatus,
                    amount,
                    occurredAt,
                    rawMap
            );
        } catch (Exception e) {
            log.error("Failed to parse and verify Midtrans webhook body", e);
            throw new IllegalArgumentException("Invalid Midtrans webhook notification: " + e.getMessage(), e);
        }
    }

    private void validateSignature(MidtransNotificationPayload payload) {
        if (payload.signature_key() == null || payload.signature_key().isBlank()) {
            throw new IllegalArgumentException("Missing signature in Midtrans webhook notification");
        }
        // SHA512(order_id+status_code+gross_amount+ServerKey)
        String stringToHash = payload.order_id() + payload.status_code() + payload.gross_amount() + properties.serverKey();

        String expectedSignature = generateSignatureKey(stringToHash);

        boolean valid = MessageDigest.isEqual(expectedSignature.getBytes(
                        StandardCharsets.US_ASCII),
                payload.signature_key().getBytes(StandardCharsets.US_ASCII));

        if (!valid) {
            throw new ServiceException(PaymentError.INVALID_MIDTRANS_WEBHOOK);
        }
    }

    private String generateSignatureKey(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");

            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-512 seharusnya tersedia pada implementasi Java standar.
            // Jika tidak tersedia, ini masalah lingkungan/runtime,
            // bukan signature webhook yang tidak valid.
            throw new IllegalStateException(
                    "SHA-512 algorithm not available",
                    e
            );
        }
    }


    private MidtransChargeRequest generateRequestPayload(ChargeRequest request) {
        var paymentConfig = switch (request.channelType()) {
            case VA -> new PaymentConfig(
                    "bank_transfer",
                    null,
                    new MidtransChargeRequest.BankTransferConfig(
                            request.providerChannelCode()
                    )
            );
            case QRIS -> new PaymentConfig(
                    "qris",
                    new MidtransChargeRequest.QrisConfig(
                            request.providerChannelCode()
                    ),
                    null
            );
            default -> throw new IllegalStateException("Unsupported channel type: " + request.channelType());
        };

        var trxDetails = new MidtransChargeRequest.TransactionDetails(
                request.orderId(),
                request.amount()
        );

        var customerDetails = new MidtransChargeRequest.CustomerDetails(
                request.customerName(),
                null,
                request.customerEmail(),
                null
        );

        return new MidtransChargeRequest(
                paymentConfig.paymentType,
                trxDetails,
                customerDetails,
                paymentConfig.qris,
                paymentConfig.bankTransfer
        );
    }

    private record PaymentConfig(
            String paymentType,
            MidtransChargeRequest.QrisConfig qris,
            MidtransChargeRequest.BankTransferConfig bankTransfer
    ) {
    }

    private ChargeResult toChargeResult(
            ChargeRequest req,
            MidtransChargeResponse res,
            Map<String, Object> rawRequest,
            Map<String, Object> rawResponse
    ) {
        var paymentReferenceNumber = switch (req.channelType()) {
            case VA -> res.va_numbers().getFirst().va_number();
            case QRIS -> res.qr_string();
            default -> throw new IllegalStateException(
                    "Unsupported channel type: " + req.channelType()
            );
        };

        return new ChargeResult(
                res.order_id(),
                res.transaction_id(),
                paymentReferenceNumber,
                parseTime(res.expiry_time()),
                rawRequest,
                rawResponse
        );
    }

    // MAPPING
    private PaymentAttemptStatus mapAttemptStatus(String status) {
        return switch (status) {
            case "settlement" -> PaymentAttemptStatus.PAID;
            case "pending" -> PaymentAttemptStatus.PENDING;
            case "expire" -> PaymentAttemptStatus.EXPIRED;
            case "deny", "cancel" -> PaymentAttemptStatus.FAILED;
            default -> PaymentAttemptStatus.PENDING;
        };
    }

    private ProcessedEventType mapEventType(String status) {
        return switch (status) {
            case "pending" -> ProcessedEventType.PAYMENT_PENDING;
            case "settlement" -> ProcessedEventType.PAYMENT_PAID;
            case "expire" -> ProcessedEventType.PAYMENT_EXPIRED;
            default -> ProcessedEventType.PAYMENT_FAILED;
        };
    }

    static ProviderStatus toProviderStatus(
            MidtransGetStatusResponse response,
            Map<String, Object> rawResponse
    ) {
        PaymentAttemptStatus status = switch (response.transaction_status()) {
            case "pending" -> PaymentAttemptStatus.PENDING;
            case "settlement" -> PaymentAttemptStatus.PAID;
            case "failed" -> PaymentAttemptStatus.FAILED;
            case "expire" -> PaymentAttemptStatus.EXPIRED;
            default -> PaymentAttemptStatus.INITIATED;
        };
        return new ProviderStatus(
                response.transaction_id(),
                status,
                null, // ga ngasih midtrans
                rawResponse
        );
    }

    // time
    private static final DateTimeFormatter MIDTRANS_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ZoneId JAKARTA_ZONE =
            ZoneId.of("Asia/Jakarta");

    /**
     * PG tidak selalu mengembalikan waktu (mis. {@code expiry_time} kosong).
     * Kembalikan {@code null} apa adanya — jangan dibuat "sekarang", karena itu
     * membuat charge tampak langsung kedaluwarsa. Pemanggil yang butuh nilai
     * (mis. {@code occurredAt}) memberi fallback sendiri.
     */
    private Instant parseTime(String timeStr) {
        if (timeStr == null) return null;
        return LocalDateTime.parse(timeStr, MIDTRANS_EXPIRY_FORMAT)
                .atZone(JAKARTA_ZONE)
                .toInstant();
    }
}
