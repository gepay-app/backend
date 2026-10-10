package com.gepe.gepay.payment.internal.config;

import com.gepe.gepay.payment.internal.job.PayoutJob;
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
 * Mendaftarkan job payout periodik pada scheduler Quartz clustered. Cron bisa
 * dioverride lewat {@code payment.payout.cron} (default tiap 5 menit).
 */
@Configuration(proxyBeanMethods = false)
public class PayoutScheduler {

    static final String JOB_KEY = "payoutJob";
    static final String TRIGGER_KEY = "payoutTrigger";
    static final TimeZone JAKARTA = TimeZone.getTimeZone("Asia/Jakarta");

    @Bean
    JobDetail payoutJobDetail() {
        return JobBuilder.newJob(PayoutJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably(true)
                .build();
    }

    @Bean
    Trigger payoutTrigger(
            JobDetail payoutJobDetail,
            @Value("${payment.payout.cron}") String cron
    ) {
        return TriggerBuilder.newTrigger()
                .forJob(payoutJobDetail)
                .withIdentity(TRIGGER_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(JAKARTA)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
