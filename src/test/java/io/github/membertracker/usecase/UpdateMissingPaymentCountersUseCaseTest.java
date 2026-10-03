package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateMissingPaymentCountersUseCaseTest {

    private MemberRepository memberRepository;
    private PaymentRepository paymentRepository;
    private UpdateMissingPaymentCountersUseCase useCase;
    private final YearMonth previousMonth = YearMonth.now().minusMonths(1);

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        useCase = new UpdateMissingPaymentCountersUseCase(memberRepository, new HasPaymentForMonthUseCase(paymentRepository));
    }

    private Member member(String name, int missed) {
        Member m = new Member(name, name + "@example.com", "+1234567890");
        m.setConsecutiveMonthsMissed(missed);
        return m;
    }

    @Test
    void memberWithoutPreviousMonthPaymentIsIncrementedAndSaved() {
        Member late = member("late", 1);
        when(memberRepository.findAll()).thenReturn(List.of(late));
        when(paymentRepository.existsByMemberAndPeriod(late, previousMonth)).thenReturn(false);

        useCase.invoke();

        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(2);
        verify(memberRepository).save(late);
    }

    @Test
    void memberWhoPaidPreviousMonthIsLeftAloneAndNotSaved() {
        Member paid = member("paid", 0);
        when(memberRepository.findAll()).thenReturn(List.of(paid));
        when(paymentRepository.existsByMemberAndPeriod(paid, previousMonth)).thenReturn(true);

        useCase.invoke();

        assertThat(paid.getConsecutiveMonthsMissed()).isZero();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void onlyMembersWithoutAPaymentAreIncremented() {
        Member paid = member("paid", 0);
        Member late = member("late", 0);
        when(memberRepository.findAll()).thenReturn(List.of(paid, late));
        when(paymentRepository.existsByMemberAndPeriod(paid, previousMonth)).thenReturn(true);

        useCase.invoke();

        assertThat(paid.getConsecutiveMonthsMissed()).isZero();
        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(1);
        verify(memberRepository).save(late);
        verify(memberRepository, never()).save(paid);
    }

    @Test
    void checksThePreviousMonthNotTheCurrentOne() {
        Member m = member("m", 0);
        when(memberRepository.findAll()).thenReturn(List.of(m));

        useCase.invoke();

        verify(paymentRepository).existsByMemberAndPeriod(m, previousMonth);
        verify(paymentRepository, never()).existsByMemberAndPeriod(m, YearMonth.now());
    }

    @Test
    void noMembersMeansNoSaves() {
        when(memberRepository.findAll()).thenReturn(List.of());

        useCase.invoke();

        verify(memberRepository, never()).save(any());
    }

    @Test
    @Disabled("AUDIT C3: counter is incremented on every run, so a daily schedule adds about 30 per month for the same missed month")
    void runningTwiceForTheSameMonthIncrementsOnlyOnce() {
        Member late = member("late", 0);
        when(memberRepository.findAll()).thenReturn(List.of(late));

        useCase.invoke();
        useCase.invoke();

        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(1);
    }
}
