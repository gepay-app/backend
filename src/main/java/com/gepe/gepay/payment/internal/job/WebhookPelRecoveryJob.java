package com.gepe.gepay.payment.internal.job;

import com.gepe.gepay.payment.internal.config.WebhookConsumerName;
import com.gepe.gepay.payment.internal.config.WebhookRedisStreamConfig;
import com.gepe.gepay.payment.internal.delivery.http.WebhookController;
import com.gepe.gepay.payment.internal.service.WebhookStreamConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job Quartz untuk memulihkan pesan webhook yang menggantung di Redis Stream Pending Entries List (PEL)
 * ketika worker/DB mengalami crash saat pemrosesan.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class WebhookPelRecoveryJob extends QuartzJobBean {

    private final StringRedisTemplate redisTemplate;
    private final WebhookStreamConsumer consumer;
    private final WebhookConsumerName consumerName;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.debug("Running Webhook PEL Recovery Job...");

        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    WebhookController.WEBHOOK_STREAM_KEY,
                    WebhookRedisStreamConfig.CONSUMER_GROUP,
                    Range.unbounded(),
                    100
            );

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                log.debug("Webhook PEL Recovery Job has no pending messages");
                return;
            }

            for (PendingMessage pending : pendingMessages) {
                if (pending.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofMinutes(5)) > 0) {
                    log.warn("Re-claiming idle PEL message id={} to consumer={}", pending.getIdAsString(), consumerName.value());

                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().claim(
                            WebhookController.WEBHOOK_STREAM_KEY,
                            WebhookRedisStreamConfig.CONSUMER_GROUP,
                            consumerName.value(),
                            Duration.ofMinutes(5),
                            pending.getId()
                    );

                    for (MapRecord<String, Object, Object> record : records) {
                        Map<String, String> stringValueMap = new HashMap<>();
                        record.getValue().forEach((k, v) -> stringValueMap.put(String.valueOf(k), String.valueOf(v)));

                        MapRecord<String, String, String> stringRecord = MapRecord.create(record.getStream(), stringValueMap)
                                .withId(record.getId());

                        consumer.onMessage(stringRecord);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute Webhook PEL Recovery Job", e);
        }
    }
}
