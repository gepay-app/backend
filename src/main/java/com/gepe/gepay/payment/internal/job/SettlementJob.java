package com.gepe.gepay.payment.internal.job;

import com.gepe.gepay.payment.internal.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Job Quartz harian (clustered) yang men-settle payment {@code PAID} yang sudah
 * lewat {@code expected_settlement_date}. Idempoten: payment yang sudah punya
 * {@code settlement_id} tidak ikut lagi; jurnal dilindungi
 * {@code journals.idempotency_key}.
 */
@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class SettlementJob extends QuartzJobBean {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    private final SettlementService settlementService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate today = LocalDate.now(JAKARTA);
        log.info("Running settlement job for {}", today);
        try {
            settlementService.settleDuePayments(today);
        } catch (Exception e) {
            log.error("Settlement job failed for {}", today, e);
        }
    }
}
