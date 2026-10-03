package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    private Member newMember() {
        return new Member("Jane Doe", "jane@example.com", "+1234567890");
    }

    private Payment paymentFor(Member member, YearMonth period) {
        return new Payment(member, period, 25.0, PaymentMethod.CASH);
    }

    @Test
    void newMemberIsActiveWithNoMissedPayments() {
        Member m = newMember();
        assertThat(m.isActive()).isTrue();
        assertThat(m.getConsecutiveMonthsMissed()).isZero();
        assertThat(m.getJoinDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void recordPayment_forCurrentPeriod_resetsMissedCounterAndSetsLastPaymentDate() {
        Member m = newMember();
        m.setConsecutiveMonthsMissed(2);
        Payment p = paymentFor(m, YearMonth.now());
        p.setPaymentDate(LocalDate.of(2024, 5, 3));

        m.recordPayment(p);

        assertThat(m.getConsecutiveMonthsMissed()).isZero();
        assertThat(m.getLastPaymentDate()).isEqualTo(LocalDate.of(2024, 5, 3));
    }

    @Test
    void recordPayment_forOtherPeriod_keepsMissedCounterButUpdatesLastPaymentDate() {
        Member m = newMember();
        m.setConsecutiveMonthsMissed(1);
        Payment p = paymentFor(m, YearMonth.now().minusMonths(1));
        p.setPaymentDate(LocalDate.of(2024, 5, 3));

        m.recordPayment(p);

        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(1);
        assertThat(m.getLastPaymentDate()).isEqualTo(LocalDate.of(2024, 5, 3));
    }

    @Test
    void recordPayment_withoutPeriod_doesNotResetCounter() {
        Member m = newMember();
        m.setConsecutiveMonthsMissed(1);
        Payment p = paymentFor(m, null);

        m.recordPayment(p);

        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(1);
    }

    @Test
    void recordPayment_null_throws() {
        assertThatThrownBy(() -> newMember().recordPayment(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivate_thenActivate_resetsMissedCounter() {
        Member m = newMember();
        m.setConsecutiveMonthsMissed(1);
        m.deactivate();
        assertThat(m.isActive()).isFalse();

        m.activate();

        assertThat(m.isActive()).isTrue();
        assertThat(m.getConsecutiveMonthsMissed()).isZero();
    }

    @Test
    void activate_whenAlreadyActive_throws() {
        assertThatThrownBy(() -> newMember().activate())
            .isInstanceOf(MemberDomainException.class)
            .extracting("errorCode").isEqualTo(MemberDomainException.MEMBER_ALREADY_ACTIVE);
    }

    @Test
    void deactivate_whenAlreadyInactive_throws() {
        Member m = newMember();
        m.deactivate();
        assertThatThrownBy(m::deactivate)
            .isInstanceOf(MemberDomainException.class)
            .extracting("errorCode").isEqualTo(MemberDomainException.MEMBER_ALREADY_INACTIVE);
    }

    @Test
    void markMissedFor_firstCallIncrementsAndRemembersTheMonth() {
        Member m = newMember();

        boolean changed = m.markMissedFor(YearMonth.of(2026, 9));

        assertThat(changed).isTrue();
        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(1);
        assertThat(m.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void markMissedFor_sameMonthAgainChangesNothing() {
        Member m = newMember();
        m.markMissedFor(YearMonth.of(2026, 9));

        boolean changed = m.markMissedFor(YearMonth.of(2026, 9));

        assertThat(changed).isFalse();
        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(1);
        assertThat(m.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void markMissedFor_laterMonthIncrementsAgain() {
        Member m = newMember();
        m.markMissedFor(YearMonth.of(2026, 9));

        boolean changed = m.markMissedFor(YearMonth.of(2026, 10));

        assertThat(changed).isTrue();
        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(2);
        assertThat(m.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 10));
    }
}
