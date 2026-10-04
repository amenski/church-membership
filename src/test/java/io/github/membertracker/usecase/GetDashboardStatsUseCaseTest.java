package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.usecase.GetDashboardStatsUseCase.DashboardStats;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDashboardStatsUseCaseTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);

    @Test
    void combinesTheCountsAndThisMonthsRevenue() {
        when(memberRepository.countNotArchived()).thenReturn(10L);
        when(memberRepository.countDuesPaying()).thenReturn(8L);
        when(memberRepository.countDuesPayingWithMissedAtLeast(1)).thenReturn(3L);
        when(paymentRepository.sumAmountByPeriod(YearMonth.now())).thenReturn(120.5);

        DashboardStats stats = new GetDashboardStatsUseCase(memberRepository, paymentRepository).invoke();

        assertThat(stats).isEqualTo(new DashboardStats(10, 8, 3, 120.5));
    }
}
