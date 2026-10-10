package com.gepe.gepay.payment.internal.config;

import com.gepe.gepay.payment.internal.job.WebhookPelRecoveryJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Mendaftarkan job pemulihan webhook (Redis Stream PEL) pada scheduler Quartz
 * clustered. Sebelumnya {@link WebhookPelRecoveryJob} ada tetapi tidak pernah
 * dijadwalkan, sehingga pesan webhook yang menggantung saat crash tidak pernah
 * dicoba ulang. Cron bisa dioverride lewat {@code payment.webhook.recovery-cron}
 * (default tiap 1 menit).
 */
@Configuration(proxyBeanMethods = false)
public class WebhookPelRecoveryScheduler {

    static final String JOB_KEY = "webhookPelRecoveryJob";
    static final String TRIGGER_KEY = "webhookPelRecoveryTrigger";
    static final TimeZone JAKARTA = TimeZone.getTimeZone("Asia/Jakarta");

    @Bean
    JobDetail webhookPelRecoveryJobDetail() {
        return JobBuilder.newJob(WebhookPelRecoveryJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably(true)
                .build();
    }

    @Bean
    Trigger webhookPelRecoveryTrigger(
            JobDetail webhookPelRecoveryJobDetail,
            @Value("${payment.webhook.recovery-cron}") String cron
    ) {
        return TriggerBuilder.newTrigger()
                .forJob(webhookPelRecoveryJobDetail)
                .withIdentity(TRIGGER_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(JAKARTA)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
