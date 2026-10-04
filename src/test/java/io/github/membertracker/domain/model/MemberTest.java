package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.MemberStatus;
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
        assertThat(m.getStatus().countsForDues()).isTrue();
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
    void recordPayment_neverMovesLastPaymentDateBackwards() {
        Member m = newMember();
        m.setLastPaymentDate(LocalDate.of(2026, 9, 1));
        Payment older = paymentFor(m, YearMonth.of(2024, 3));
        older.setPaymentDate(LocalDate.of(2024, 3, 10));

        m.recordPayment(older);

        assertThat(m.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 1));

        Payment newer = paymentFor(m, YearMonth.of(2024, 4));
        newer.setPaymentDate(LocalDate.of(2026, 9, 15));
        m.recordPayment(newer);

        assertThat(m.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 15));
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
        assertThat(m.getStatus().countsForDues()).isFalse();

        m.activate();

        assertThat(m.getStatus().countsForDues()).isTrue();
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

    @Test
    void newMemberHasTheStatusMember() {
        assertThat(newMember().getStatus()).isEqualTo(MemberStatus.MEMBER);
        assertThat(new Member().getStatus()).isEqualTo(MemberStatus.MEMBER);
    }

    @Test
    void activeIsDerivedFromTheStatus() {
        Member m = newMember();
        for (MemberStatus status : MemberStatus.values()) {
            m.setStatus(status);
            assertThat(m.getStatus().countsForDues()).as(status.name()).isEqualTo(status == MemberStatus.MEMBER);
        }
    }

    @Test
    void activate_fromEveryOtherStatus_makesAMemberAndResetsTheCounter() {
        for (MemberStatus from : new MemberStatus[] {MemberStatus.INACTIVE, MemberStatus.DECEASED, MemberStatus.TRANSFERRED}) {
            Member m = newMember();
            m.setStatus(from);
            m.setConsecutiveMonthsMissed(3);

            m.activate();

            assertThat(m.getStatus()).as(from.name()).isEqualTo(MemberStatus.MEMBER);
            assertThat(m.getConsecutiveMonthsMissed()).as(from.name()).isZero();
        }
    }

    @Test
    void deactivate_fromAnotherStatusMakesInactiveAndKeepsTheCounter() {
        Member m = newMember();
        m.setStatus(MemberStatus.TRANSFERRED);
        m.setConsecutiveMonthsMissed(3);

        m.deactivate();

        assertThat(m.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(m.getConsecutiveMonthsMissed()).isEqualTo(3);
    }
}
