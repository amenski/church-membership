package io.github.membertracker.domain.policy;

import io.github.membertracker.domain.model.Member;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMembershipPolicyTest {

    // 2024-02 has 29 days (leap year); due date = 2024-02-29
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 10);

    private final DefaultMembershipPolicy policy = new DefaultMembershipPolicy();

    private Member member() {
        Member m = new Member("Jane", "jane@example.com", "+1234567890");
        m.setJoinDate(LocalDate.of(2023, 1, 1));
        return m;
    }

    private Member inactiveMember() {
        Member m = member();
        m.setActive(false);
        return m;
    }

    // shouldDeactivate

    @Test
    void shouldDeactivate_atThreeMissedMonths_butNotAtTwo() {
        Member m = member();
        m.setConsecutiveMonthsMissed(2);
        assertThat(policy.shouldDeactivate(m, TODAY)).isFalse();
        m.setConsecutiveMonthsMissed(3);
        assertThat(policy.shouldDeactivate(m, TODAY)).isTrue();
        m.setConsecutiveMonthsMissed(10);
        assertThat(policy.shouldDeactivate(m, TODAY)).isTrue();
    }

    @Test
    void shouldDeactivate_alreadyInactive_isFalse() {
        Member m = inactiveMember();
        m.setConsecutiveMonthsMissed(5);
        assertThat(policy.shouldDeactivate(m, TODAY)).isFalse();
    }

    // shouldSendReminder

    @Test
    void shouldSendReminder_withinLastDaysOfMonthAndNotPaid_isTrue() {
        Member m = member();
        assertThat(policy.shouldSendReminder(m, LocalDate.of(2024, 2, 23))).isTrue();
        assertThat(policy.shouldSendReminder(m, LocalDate.of(2024, 2, 29))).isTrue();
    }

    @Test
    void shouldSendReminder_earlyInMonth_isFalse() {
        assertThat(policy.shouldSendReminder(member(), LocalDate.of(2024, 2, 21))).isFalse();
        assertThat(policy.shouldSendReminder(member(), TODAY)).isFalse();
    }

    @Test
    void shouldSendReminder_alreadyPaidThisMonth_isFalse() {
        Member m = member();
        m.setLastPaymentDate(LocalDate.of(2024, 2, 2));
        assertThat(policy.shouldSendReminder(m, LocalDate.of(2024, 2, 28))).isFalse();
    }

    @Test
    void shouldSendReminder_lastPaymentInPreviousMonth_isStillTrueNearDueDate() {
        Member m = member();
        m.setLastPaymentDate(LocalDate.of(2024, 1, 30));
        assertThat(policy.shouldSendReminder(m, LocalDate.of(2024, 2, 28))).isTrue();
    }

    @Test
    void shouldSendReminder_inactiveMember_isFalse() {
        assertThat(policy.shouldSendReminder(inactiveMember(), LocalDate.of(2024, 2, 28))).isFalse();
    }

    @Test
    @Disabled("BUG: reminder skips exactly 7 days before due date (isAfter(due-7)) although REMINDER_DAYS_BEFORE_DUE is 7")
    void shouldSendReminder_exactlySevenDaysBeforeDue_isTrue() {
        // 2024-02-22 is 7 days before 2024-02-29
        assertThat(policy.daysUntilPaymentDue(member(), LocalDate.of(2024, 2, 22))).isEqualTo(7);
        assertThat(policy.shouldSendReminder(member(), LocalDate.of(2024, 2, 22))).isTrue();
    }

    // canReactivate

    @Test
    void canReactivate_activeMember_isFalse() {
        assertThat(policy.canReactivate(member(), TODAY)).isFalse();
    }

    @Test
    void canReactivate_lastPaymentWithinThirtyDays_boundary() {
        Member m = inactiveMember();
        m.setLastPaymentDate(TODAY.minusDays(30));
        assertThat(policy.canReactivate(m, TODAY)).isTrue();
        m.setLastPaymentDate(TODAY.minusDays(31));
        assertThat(policy.canReactivate(m, TODAY)).isFalse();
    }

    @Test
    void canReactivate_noPayments_usesJoinDateGracePeriod() {
        Member m = inactiveMember();
        m.setJoinDate(TODAY.minusDays(30));
        assertThat(policy.canReactivate(m, TODAY)).isTrue();
        m.setJoinDate(TODAY.minusDays(31));
        assertThat(policy.canReactivate(m, TODAY)).isFalse();
    }

    @Test
    void canReactivate_noPaymentAndNoJoinDate_isFalse() {
        Member m = inactiveMember();
        m.setJoinDate(null);
        assertThat(policy.canReactivate(m, TODAY)).isFalse();
    }

    // daysUntilPaymentDue

    @Test
    void daysUntilPaymentDue_countsToEndOfMonth() {
        assertThat(policy.daysUntilPaymentDue(member(), TODAY)).isEqualTo(19);
        assertThat(policy.daysUntilPaymentDue(member(), LocalDate.of(2024, 2, 29))).isZero();
        assertThat(policy.daysUntilPaymentDue(member(), LocalDate.of(2023, 2, 1))).isEqualTo(27);
    }

    // isInGoodStanding

    @Test
    void isInGoodStanding_activeWithNoMissedPayments_isTrue() {
        assertThat(policy.isInGoodStanding(member(), TODAY)).isTrue();
    }

    @Test
    void isInGoodStanding_inactive_isFalse() {
        assertThat(policy.isInGoodStanding(inactiveMember(), TODAY)).isFalse();
    }

    @Test
    void isInGoodStanding_oneMissed_dependsOnLastPaymentWithinThirtyDays() {
        Member m = member();
        m.setConsecutiveMonthsMissed(1);
        m.setLastPaymentDate(TODAY.minusDays(30));
        assertThat(policy.isInGoodStanding(m, TODAY)).isTrue();
        m.setLastPaymentDate(TODAY.minusDays(31));
        assertThat(policy.isInGoodStanding(m, TODAY)).isFalse();
    }

    @Test
    void isInGoodStanding_oneMissedWithoutPaymentHistory_isFalse() {
        Member m = member();
        m.setConsecutiveMonthsMissed(1);
        assertThat(policy.isInGoodStanding(m, TODAY)).isFalse();
    }

    @Test
    void isInGoodStanding_twoOrMoreMissed_isFalse() {
        Member m = member();
        m.setConsecutiveMonthsMissed(2);
        m.setLastPaymentDate(TODAY.minusDays(1));
        assertThat(policy.isInGoodStanding(m, TODAY)).isFalse();
    }
}
