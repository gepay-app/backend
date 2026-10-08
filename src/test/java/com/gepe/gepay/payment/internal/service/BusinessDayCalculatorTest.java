package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.HolidayRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDayCalculatorTest {

    private static final LocalDate RABU_7_OKTOBER = LocalDate.of(2026, 10, 7);
    private static final LocalDate KAMIS_8_OKTOBER = LocalDate.of(2026, 10, 8);
    private static final LocalDate JUMAT_9_OKTOBER = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABTU_10_OKTOBER = LocalDate.of(2026, 10, 10);
    private static final LocalDate MINGGU_11_OKTOBER = LocalDate.of(2026, 10, 11);
    private static final LocalDate SENIN_12_OKTOBER = LocalDate.of(2026, 10, 12);
    private static final LocalDate SELASA_13_OKTOBER = LocalDate.of(2026, 10, 13);

    @Mock
    private HolidayRepository holidayRepository;

    @InjectMocks
    private BusinessDayCalculator businessDayCalculator;

    @Test
    @DisplayName("isBusinessDay returns true for a weekday that is not a holiday")
    void isBusinessDay_WeekdayNotHoliday_ReturnsTrue() {
        when(holidayRepository.existsByDate(KAMIS_8_OKTOBER)).thenReturn(false);

        assertThat(businessDayCalculator.isBusinessDay(KAMIS_8_OKTOBER)).isTrue();
    }

    @Test
    @DisplayName("isBusinessDay returns false on Saturday without querying holidays")
    void isBusinessDay_Saturday_ReturnsFalse() {
        assertThat(businessDayCalculator.isBusinessDay(SABTU_10_OKTOBER)).isFalse();

        verify(holidayRepository, never()).existsByDate(SABTU_10_OKTOBER);
    }

    @Test
    @DisplayName("isBusinessDay returns false on Sunday without querying holidays")
    void isBusinessDay_Sunday_ReturnsFalse() {
        assertThat(businessDayCalculator.isBusinessDay(MINGGU_11_OKTOBER)).isFalse();

        verify(holidayRepository, never()).existsByDate(MINGGU_11_OKTOBER);
    }

    @Test
    @DisplayName("isBusinessDay returns false for a weekday that is a national holiday")
    void isBusinessDay_WeekdayHoliday_ReturnsFalse() {
        when(holidayRepository.existsByDate(KAMIS_8_OKTOBER)).thenReturn(true);

        assertThat(businessDayCalculator.isBusinessDay(KAMIS_8_OKTOBER)).isFalse();
    }

    @Test
    @DisplayName("plusBusinessDays returns the same date when businessDays is zero")
    void plusBusinessDays_Zero_ReturnsSameDate() {
        assertThat(businessDayCalculator.plusBusinessDays(RABU_7_OKTOBER, 0))
                .isEqualTo(RABU_7_OKTOBER);

        verify(holidayRepository, never()).existsByDate(any());
    }

    @Test
    @DisplayName("plusBusinessDays skips weekends")
    void plusBusinessDays_SkipsWeekend() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);

        assertThat(businessDayCalculator.plusBusinessDays(JUMAT_9_OKTOBER, 1))
                .isEqualTo(SENIN_12_OKTOBER);
    }

    @Test
    @DisplayName("plusBusinessDays skips weekends and national holidays")
    void plusBusinessDays_SkipsWeekendAndHoliday() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(holidayRepository.existsByDate(KAMIS_8_OKTOBER)).thenReturn(true);

        // Rabu 7 + 3 hari kerja:
        // 8 (libur) -> 9 Jumat (T+1) -> 10/11 weekend -> 12 Senin (T+2) -> 13 Selasa (T+3)
        assertThat(businessDayCalculator.plusBusinessDays(RABU_7_OKTOBER, 3))
                .isEqualTo(SELASA_13_OKTOBER);
    }

    @Test
    @DisplayName("plusBusinessDays rejects negative businessDays")
    void plusBusinessDays_Negative_Throws() {
        assertThatThrownBy(() -> businessDayCalculator.plusBusinessDays(RABU_7_OKTOBER, -1))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(PaymentError.INVALID_BUSINESS_DAYS));
    }
}
