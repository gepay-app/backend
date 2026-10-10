package com.gepe.gepay.donation.internal.config;

import com.gepe.gepay.donation.internal.service.DonationStatusStreamService;
import com.gepe.gepay.donation.internal.service.RedisOverlayBroadcaster;
import com.gepe.gepay.donation.internal.ws.DonationStatusRedisSubscriber;
import com.gepe.gepay.donation.internal.ws.OverlayRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub donation: fan-out perintah play overlay
 * ({@code donation:overlay:play:*}), rotasi key ({@code donation:overlay:key-rotated:*})
 * dan sinyal PAID ({@code donation:payment:*}) ke sesi WebSocket/SSE lokal tiap node.
 */
@Configuration(proxyBeanMethods = false)
public class DonationRedisConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            OverlayRedisSubscriber overlaySubscriber,
            DonationStatusRedisSubscriber statusSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(overlaySubscriber,
                new PatternTopic(RedisOverlayBroadcaster.PLAY_CHANNEL_PREFIX + "*"));
        container.addMessageListener(overlaySubscriber,
                new PatternTopic(RedisOverlayBroadcaster.KEY_ROTATED_CHANNEL_PREFIX + "*"));
        container.addMessageListener(statusSubscriber,
                new PatternTopic(DonationStatusStreamService.PAID_CHANNEL_PREFIX + "*"));
        return container;
    }
}
