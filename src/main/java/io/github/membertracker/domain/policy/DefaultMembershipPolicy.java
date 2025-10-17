package io.github.membertracker.domain.policy;

import io.github.membertracker.domain.model.Member;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Default implementation of the MembershipPolicy interface.
 * Contains the core business rules for membership management, payment reminders, and member status.
 * This implementation can be easily replaced or extended for different business requirements.
 */
public class DefaultMembershipPolicy implements MembershipPolicy {

    // Configuration constants for business rules
    private static final int MAX_CONSECUTIVE_MISSED_MONTHS = 3;
    private static final int REMINDER_DAYS_BEFORE_DUE = 7;
    private static final int REACTIVATION_GRACE_PERIOD_DAYS = 30;
    private static final double MINIMUM_PAYMENT_AMOUNT = 10.0;

    @Override
    public boolean shouldDeactivate(Member member, LocalDate currentDate) {
        if (!member.isActive()) {
            return false;
        }
        return member.getConsecutiveMonthsMissed() >= MAX_CONSECUTIVE_MISSED_MONTHS;
    }

    @Override
    public boolean shouldSendReminder(Member member, LocalDate currentDate) {
        if (!member.isActive()) {
            return false; // Don't send reminders to inactive members
        }

        YearMonth currentMonth = YearMonth.from(currentDate);
        LocalDate dueDate = currentMonth.atEndOfMonth();
        
        // Send reminder if we're within REMINDER_DAYS_BEFORE_DUE of the due date
        // and the member hasn't paid for the current month
        boolean isWithinReminderPeriod = currentDate.isAfter(dueDate.minusDays(REMINDER_DAYS_BEFORE_DUE));
        boolean hasNotPaidForCurrentMonth = !hasPaymentForMonth(member, currentMonth);
        
        return isWithinReminderPeriod && hasNotPaidForCurrentMonth;
    }

    @Override
    public boolean canReactivate(Member member, LocalDate currentDate) {
        if (member.isActive()) {
            return false; // Already active
        }

        // Check if the member was deactivated within the grace period
        if (member.getLastPaymentDate() != null) {
            long daysSinceLastPayment = ChronoUnit.DAYS.between(member.getLastPaymentDate(), currentDate);
            return daysSinceLastPayment <= REACTIVATION_GRACE_PERIOD_DAYS;
        }

        // If no payment history, allow reactivation within grace period from join date
        if (member.getJoinDate() != null) {
            long daysSinceJoining = ChronoUnit.DAYS.between(member.getJoinDate(), currentDate);
            return daysSinceJoining <= REACTIVATION_GRACE_PERIOD_DAYS;
        }

        return false;
    }

    @Override
    public int daysUntilPaymentDue(Member member, LocalDate currentDate) {
        YearMonth currentMonth = YearMonth.from(currentDate);
        LocalDate dueDate = currentMonth.atEndOfMonth();
        
        return (int) ChronoUnit.DAYS.between(currentDate, dueDate);
    }

    @Override
    public boolean isInGoodStanding(Member member, LocalDate currentDate) {
        if (!member.isActive()) {
            return false;
        }

        // Member is in good standing if they have no missed payments
        // or have missed only 1 month but are still within the grace period
        if (member.getConsecutiveMonthsMissed() == 0) {
            return true;
        }

        if (member.getConsecutiveMonthsMissed() == 1 && member.getLastPaymentDate() != null) {
            long daysSinceLastPayment = ChronoUnit.DAYS.between(member.getLastPaymentDate(), currentDate);
            return daysSinceLastPayment <= REACTIVATION_GRACE_PERIOD_DAYS;
        }

        return false;
    }

    /**
     * Helper method to check if a member has a payment for a specific month.
     * This would typically be implemented by querying the payment repository,
     * but for the policy pattern, we assume this information is available or can be determined.
     */
    private boolean hasPaymentForMonth(Member member, YearMonth month) {
        // In a real implementation, this would check the payment repository
        // For now, we'll use a simplified logic based on last payment date
        if (member.getLastPaymentDate() == null) {
            return false;
        }
        
        YearMonth lastPaymentMonth = YearMonth.from(member.getLastPaymentDate());
        return lastPaymentMonth.equals(month);
    }

    // Configuration getters (could be made configurable via properties)
    public int getMaxConsecutiveMissedMonths() {
        return MAX_CONSECUTIVE_MISSED_MONTHS;
    }

    public int getReminderDaysBeforeDue() {
        return REMINDER_DAYS_BEFORE_DUE;
    }

    public int getReactivationGracePeriodDays() {
        return REACTIVATION_GRACE_PERIOD_DAYS;
    }

    public double getMinimumPaymentAmount() {
        return MINIMUM_PAYMENT_AMOUNT;
    }
}