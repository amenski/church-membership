package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.PaymentDomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Member activeMember() {
        return new Member("Jane Doe", "jane@example.com", "+1234567890");
    }

    private Payment payment(YearMonth period, Double amount) {
        return new Payment(activeMember(), period, amount, PaymentMethod.CASH);
    }

    // validateAmount

    @Test
    void validateAmount_positive_passes() {
        assertThatCode(() -> payment(YearMonth.now(), 0.01).validateAmount()).doesNotThrowAnyException();
    }

    @Test
    void validateAmount_nullZeroOrNegative_throwsInvalidAmount() {
        for (Double bad : new Double[] {null, 0.0, -5.0}) {
            assertThatThrownBy(() -> payment(YearMonth.now(), bad).validateAmount())
                .isInstanceOf(PaymentDomainException.class)
                .extracting("errorCode").isEqualTo(PaymentDomainException.INVALID_PAYMENT_AMOUNT);
        }
    }

    @Test
    void validateAmount_invalid_messageSaysAmountMustBeGreaterThanZero() {
        assertThatThrownBy(() -> payment(YearMonth.now(), 0.0).validateAmount())
            .hasMessageContaining("must be greater than 0")
            .hasMessageNotContaining("10.0");
    }

    // validatePeriod

    @Test
    void validatePeriod_currentAndAnyPastMonthUpToTenYears_pass() {
        assertThatCode(() -> payment(YearMonth.now(), 10.0).validatePeriod()).doesNotThrowAnyException();
        assertThatCode(() -> payment(YearMonth.now().minusMonths(4), 10.0).validatePeriod()).doesNotThrowAnyException();
        assertThatCode(() -> payment(YearMonth.now().minusYears(10), 10.0).validatePeriod()).doesNotThrowAnyException();
    }

    @Test
    void validatePeriod_moreThanTenYearsBack_isRejectedAsATypo() {
        assertThatThrownBy(() -> payment(YearMonth.now().minusYears(10).minusMonths(1), 10.0).validatePeriod())
            .isInstanceOf(PaymentDomainException.class)
            .hasMessageContaining("too far back")
            .extracting("errorCode").isEqualTo(PaymentDomainException.INVALID_PAYMENT_PERIOD);
    }

    @Test
    void validatePaymentDate_todayAndPast_pass_future_isRejected() {
        Payment p = payment(YearMonth.now(), 10.0);
        assertThatCode(p::validatePaymentDate).doesNotThrowAnyException();
        p.setPaymentDate(LocalDate.of(2020, 1, 1));
        assertThatCode(p::validatePaymentDate).doesNotThrowAnyException();

        p.setPaymentDate(LocalDate.now().plusDays(1));
        assertThatThrownBy(p::validatePaymentDate)
            .isInstanceOf(PaymentDomainException.class)
            .extracting("errorCode").isEqualTo(PaymentDomainException.PAYMENT_DATE_IN_FUTURE);
    }

    @Test
    void validatePeriod_future_isRejected() {
        assertThatThrownBy(() -> payment(YearMonth.now().plusMonths(1), 10.0).validatePeriod())
            .isInstanceOf(PaymentDomainException.class)
            .extracting("errorCode").isEqualTo(PaymentDomainException.PAYMENT_PERIOD_IN_FUTURE);
    }

    @Test
    void validatePeriod_null_isRejected() {
        assertThatThrownBy(() -> payment(null, 10.0).validatePeriod())
            .isInstanceOf(PaymentDomainException.class)
            .extracting("errorCode").isEqualTo(PaymentDomainException.INVALID_PAYMENT_PERIOD);
    }

    // markAsProcessed

    @Test
    void markAsProcessed_setsDateWhenMissing() {
        Payment p = payment(YearMonth.now(), 10.0);
        p.setPaymentDate(null);
        p.markAsProcessed();
        assertThat(p.getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void markAsProcessed_keepsExistingDate() {
        Payment p = payment(YearMonth.now(), 10.0);
        LocalDate fixed = LocalDate.of(2024, 1, 15);
        p.setPaymentDate(fixed);
        p.markAsProcessed();
        assertThat(p.getPaymentDate()).isEqualTo(fixed);
    }
}
