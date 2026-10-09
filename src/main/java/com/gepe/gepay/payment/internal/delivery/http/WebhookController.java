package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    public static final String WEBHOOK_STREAM_KEY = "payment:webhook:stream";

    private final List<PayinProvider> payinProviders;
    private final StringRedisTemplate redisTemplate;
    private final MessageHelper messageHelper;

    @PostMapping("/{provider}")
    public ResponseEntity<Object> handleWebhook(
            @PathVariable("provider") String providerCode,
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers
    ) {
        log.info("Received webhook callback for provider={}", providerCode);

        PayinProvider provider = payinProviders.stream()
                .filter(p -> p.code().equalsIgnoreCase(providerCode))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("Unknown webhook provider: {}", providerCode);
            return error(HttpStatus.NOT_FOUND, "http.not_found");
        }

        try {
            // 1. Verifikasi signature & parse payload in-memory (< 2ms)
            provider.parseWebhook(rawBody, headers);

            // 2. Enqueue ke Redis Stream
            MapRecord<String, String, String> record = MapRecord.create(
                    WEBHOOK_STREAM_KEY,
                    Map.of(
                            "provider", provider.code(),
                            "payload", rawBody
                    )
            );
            redisTemplate.opsForStream().add(record);

            // 3. Return HTTP 200 OK langsung (< 10ms, 0 DB calls!)
            return ResponseEntity.ok(new ApiResponse<>("Webhook received successfully", null));

        } catch (ServiceException e) {
            log.warn("Rejected webhook for provider={}: {}", providerCode, e.getErrorCode().getMessageKey());
            HttpStatus status = e.getErrorCode() == PaymentError.INVALID_MIDTRANS_WEBHOOK
                    ? HttpStatus.UNAUTHORIZED
                    : e.getErrorCode().getHttpStatus();
            String code = e.getErrorCode().getMessageKey();
            return ResponseEntity.status(status)
                    .body(ErrorResponse.simple(code, messageHelper.get(code, e.getArgs())));
        } catch (Exception e) {
            log.error("Failed to process webhook payload for provider={}", providerCode, e);
            return error(HttpStatus.BAD_REQUEST, "http.bad_request");
        }
    }

    /** Failure body in the standard {@link ErrorResponse} envelope (see §6). */
    private ResponseEntity<Object> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(ErrorResponse.simple(code, messageHelper.get(code)));
    }
}
