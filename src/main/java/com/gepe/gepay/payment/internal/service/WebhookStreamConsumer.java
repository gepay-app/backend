package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.internal.config.WebhookRedisStreamConfig;
import com.gepe.gepay.payment.internal.delivery.http.WebhookController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookStreamConsumer implements StreamListener<String, MapRecord<String,String,String>> {
    private final PaymentWebhookService paymentWebhookService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String,String> value = message.getValue();
        String provider =  value.get("provider");
        String payload = value.get("payload");

        log.info("Processing webhook message from Redis Stream: id={}, provider={}", message.getId(), provider);
        try {
            // prosess transaksi dan idempoten
            paymentWebhookService.processIncomingWebhook(provider, payload);

            // acknowledge message kalo trx db sukses commit
            redisTemplate.opsForStream().acknowledge(
                    WebhookController.WEBHOOK_STREAM_KEY,
                    WebhookRedisStreamConfig.CONSUMER_GROUP,
                    message.getId()
            );
            log.info("Successfully processed & ACK webhook message: id={}", message.getId());
        } catch (Exception e) {
            log.error("Error processing webhook record id={}. Message will remain in PEL for retry.", message.getId(), e);
        }
    }
}
