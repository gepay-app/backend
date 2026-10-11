package com.gepe.gepay.payment.internal.config;

import com.gepe.gepay.payment.internal.delivery.http.WebhookController;
import com.gepe.gepay.payment.internal.service.WebhookStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.ConsumerStreamReadRequest;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.util.ErrorHandler;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class WebhookRedisStreamConfig {

    public static final String CONSUMER_GROUP = "gepay-payment-webhooks";

    @Bean
    public WebhookConsumerName webhookConsumerName(@Value("${spring.application.name:gepay}") String appName) {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = UUID.randomUUID().toString().substring(0, 8);
        }
        String processId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String consumerName = String.format("%s-%s-%s", appName, hostName, processId);
        return new WebhookConsumerName(consumerName);
    }

    @Bean
    public Subscription webhookStreamSubscription(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            WebhookStreamConsumer consumer,
            WebhookConsumerName consumerName
    ) {
        log.info("Registering Redis Stream Consumer: group='{}', consumer='{}'", CONSUMER_GROUP, consumerName.value());

        try {
            // Mulai dari awal stream supaya pesan yang di-XADD sebelum consumer
            // group dibuat tidak hilang. Kalau group sudah ada, Redis melempar
            // BUSYGROUP dan kita lanjut memakai group yang lama.
            redisTemplate.opsForStream().createGroup(
                    WebhookController.WEBHOOK_STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            log.info("Redis Stream Consumer Group '{}' already exists", CONSUMER_GROUP);
        }

        // pollTimeout = durasi BLOCK pada XREADGROUP; HARUS lebih kecil dari
        // Lettuce command timeout (spring.data.redis.timeout, default 2s) supaya
        // blocking read tidak dibatalkan client (Redis command timed out).
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        // PENTING: default Spring Data Redis men-cancel subscription pada error
        // APA PUN saat read (cancelSubscriptionOnError = t -> true). Transient
        // error seperti RedisCommandTimeoutException / reconnect akan mematikan
        // worker secara PERMANEN: aplikasi tetap hidup dan webhook tetap masuk
        // stream, tapi tidak ada lagi yang mengonsumsi (busuk di lag, bukan PEL,
        // sehingga WebhookPelRecoveryJob pun tidak bisa menolong). Di sini
        // subscription dibiarkan hidup dan retry; error cukup dicatat.
        ErrorHandler errorHandler = t ->
                log.error("Error reading Redis Stream webhook; subscription kept alive & will retry", t);

        ConsumerStreamReadRequest<String> readRequest =
                StreamReadRequest.<String>builder(
                                StreamOffset.create(WebhookController.WEBHOOK_STREAM_KEY, ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(CONSUMER_GROUP, consumerName.value()))
                        .autoAcknowledge(false)
                        .cancelOnError(t -> false)
                        .errorHandler(errorHandler)
                        .build();

        Subscription subscription = container.register(readRequest, consumer);

        container.start();
        return subscription;
    }
}