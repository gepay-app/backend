package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.HolidayRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Hitung hari kerja (business day) untuk T+n settlement. Sabtu/Minggu selalu
 * bukan hari kerja; hari libur nasional datang dari tabel {@code payment.holidays}.
 *
 * <p>PG pada kenyataannya menyelesaikan settlement dalam hari kerja dan
 * memperhitungkan hari libur nasional — karena itu T+n di
 * {@code channel_routes.settlement_delay_days} <strong>selalu</strong> dihitung
 * sebagai hari kerja (tidak ada lagi flag kalender vs hari kerja).
 */
@Service
@RequiredArgsConstructor
public class BusinessDayCalculator {

    private final HolidayRepository holidayRepository;

    /** Apakah {@code date} hari kerja (bukan Sabtu/Minggu/libur nasional). */
    public boolean isBusinessDay(@NonNull LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidayRepository.existsByDate(date);
    }

    /**
     * Tambahkan {@code businessDays} hari kerja ke {@code from}, lewati akhir
     * pekan & libur nasional.
     */
    public LocalDate plusBusinessDays(LocalDate from, int businessDays) {
        if (businessDays < 0) {
            throw new ServiceException(PaymentError.INVALID_BUSINESS_DAYS, businessDays);
        }
        LocalDate cursor = from;
        int remaining = businessDays;
        while (remaining > 0) {
            cursor = cursor.plusDays(1);
            if (isBusinessDay(cursor)) {
                remaining--;
            }
        }
        return cursor;
    }

}
