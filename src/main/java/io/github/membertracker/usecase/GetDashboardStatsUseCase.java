package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.YearMonth;

public class GetDashboardStatsUseCase {

    /** The four figures on the home screen. */
    public record DashboardStats(long totalMembers, long activeMembers, long overdueMembers, double monthlyRevenue) {
    }

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;

    public GetDashboardStatsUseCase(MemberRepository memberRepository, PaymentRepository paymentRepository) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Total is every member who is not archived, active is the dues-paying ones (status MEMBER), overdue
     * means a dues-paying member at least one month behind; revenue is the sum of the payments whose
     * billing period is the current month.
     */
    public DashboardStats invoke() {
        return new DashboardStats(
                memberRepository.countNotArchived(),
                memberRepository.countDuesPaying(),
                memberRepository.countDuesPayingWithMissedAtLeast(1),
                paymentRepository.sumAmountByPeriod(YearMonth.now()));
    }
}
