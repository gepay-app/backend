package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.internal.entity.Payment;
import com.gepe.gepay.payment.internal.entity.PaymentAttempt;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.dtos.ChargeResult;
import com.gepe.gepay.payment.internal.repository.PaymentAttemptRepository;
import com.gepe.gepay.payment.internal.repository.PaymentRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Batas transaksi untuk penulisan state payin. Dipisah dari {@code PaymentService}
 * supaya panggilan API provider (remote I/O) terjadi <strong>di luar</strong>
 * transaksi — lihat §3 transactions ("keep transactions short: no remote I/O
 * inside the transaction boundary").
 *
 * <p>{@code PaymentService} menjadi orkestrator non-transaksional: transaksi
 * #1 menyimpan payment + attempt, lalu charge provider, lalu transaksi #2
 * menyimpan hasilnya.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWriter {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    /** Pasangan payment + attempt hasil satu penulisan. */
    public record ChargeOutcome(Payment payment, PaymentAttempt attempt) {
    }

    /**
     * TX #1: simpan Payment baru + PaymentAttempt(INITIATED). Attempt dibuat
     * sebelum charge agar id-nya (UUID v7) dipakai sebagai {@code order_id} ke
     * provider. Bila {@code idempotency_key} sudah ada, unique constraint
     * melempar {@link org.springframework.dao.DataIntegrityViolationException}
     * (transaksi ikut rollback); pemanggil yang menangani replay.
     */
    @Transactional
    public PaymentAttempt insertInitiated(Payment payment, Long channelRouteId) {
        Payment saved = paymentRepository.saveAndFlush(payment);
        PaymentAttempt attempt = PaymentAttempt.initiate(saved.getId(), channelRouteId);
        paymentAttemptRepository.saveAndFlush(attempt);
        return attempt;
    }

    /**
     * TX #2: terapkan hasil charge provider + ubah status Payment & Attempt
     * menjadi PENDING. Dipanggil setelah panggilan provider selesai.
     */
    @Transactional
    public ChargeOutcome applyChargeResult(UUID paymentId, UUID attemptId, ChargeResult chargeResult) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> missing("payment", paymentId));
        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> missing("payment_attempt", attemptId));

        attempt.applyChargeResult(
                chargeResult.providerReferenceId(),
                chargeResult.paymentReferenceNumber(),
                chargeResult.expiresAt(),
                chargeResult.rawRequest(),
                chargeResult.rawResponse()
        );
        attempt.markPending();
        payment.markPending();

        paymentAttemptRepository.saveAndFlush(attempt);
        paymentRepository.saveAndFlush(payment);
        return new ChargeOutcome(payment, attempt);
    }

    /**
     * TX kompensasi: charge provider gagal setelah Payment tersimpan, jadi
     * Payment & Attempt ditandai FAILED agar tidak menggantung sebagai INITIATED.
     */
    @Transactional
    public void markChargeFailed(UUID paymentId, UUID attemptId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.markFailed();
            paymentRepository.saveAndFlush(payment);
        });
        paymentAttemptRepository.findById(attemptId).ifPresent(attempt -> {
            attempt.markFailed();
            paymentAttemptRepository.saveAndFlush(attempt);
        });
    }

    private ServiceException missing(String entity, Object id) {
        log.error("Internal invariant violated: {} {} not found while persisting charge result", entity, id);
        return new ServiceException(PaymentError.INTERNAL_ERROR);
    }
}
