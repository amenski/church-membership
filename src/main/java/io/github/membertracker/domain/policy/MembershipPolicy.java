package io.github.membertracker.domain.policy;

import io.github.membertracker.domain.model.Member;

import java.time.LocalDate;

/**
 * Policy interface for membership-related business rules.
 * Follows the policy pattern to separate cross-cutting business rules from domain models.
 * This allows for flexible and testable business rule implementations.
 */
public interface MembershipPolicy {

    /**
     * Determines if a member should be deactivated based on their payment history and current date.
     *
     * @param member the member to evaluate
     * @param currentDate the current date for evaluation
     * @return true if the member should be deactivated, false otherwise
     */
    boolean shouldDeactivate(Member member, LocalDate currentDate);

    /**
     * Determines if a payment reminder should be sent to a member.
     *
     * @param member the member to evaluate
     * @param currentDate the current date for evaluation
     * @return true if a reminder should be sent, false otherwise
     */
    boolean shouldSendReminder(Member member, LocalDate currentDate);

    /**
     * Determines if a member is eligible for reactivation.
     *
     * @param member the member to evaluate
     * @param currentDate the current date for evaluation
     * @return true if the member can be reactivated, false otherwise
     */
    boolean canReactivate(Member member, LocalDate currentDate);

    /**
     * Calculates the number of days until a member's payment is due.
     *
     * @param member the member to evaluate
     * @param currentDate the current date for evaluation
     * @return the number of days until payment is due (negative if overdue)
     */
    int daysUntilPaymentDue(Member member, LocalDate currentDate);

    /**
     * Determines if a member is in good standing.
     *
     * @param member the member to evaluate
     * @param currentDate the current date for evaluation
     * @return true if the member is in good standing, false otherwise
     */
    boolean isInGoodStanding(Member member, LocalDate currentDate);
}