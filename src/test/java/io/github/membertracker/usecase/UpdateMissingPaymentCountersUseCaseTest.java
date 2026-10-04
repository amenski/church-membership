package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
        m.setJoinDate(LocalDate.of(2020, 1, 1));
        return m;
    }

    private static Clock clockAt(String isoDate) {
        return Clock.fixed(Instant.parse(isoDate + "T08:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void memberWithoutPreviousMonthPaymentIsIncrementedAndSaved() {
        Member late = member("late", 1);
        when(memberRepository.findByActive(true)).thenReturn(List.of(late));
        when(paymentRepository.existsByMemberAndPeriod(late, previousMonth)).thenReturn(false);

        useCase.invoke();

        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(2);
        verify(memberRepository).save(late);
    }

    @Test
    void memberWhoPaidPreviousMonthIsLeftAloneAndNotSaved() {
        Member paid = member("paid", 0);
        when(memberRepository.findByActive(true)).thenReturn(List.of(paid));
        when(paymentRepository.existsByMemberAndPeriod(paid, previousMonth)).thenReturn(true);

        useCase.invoke();

        assertThat(paid.getConsecutiveMonthsMissed()).isZero();
        assertThat(paid.getLastMissedCountMonth()).isNull();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void onlyMembersWithoutAPaymentAreIncremented() {
        Member paid = member("paid", 0);
        Member late = member("late", 0);
        when(memberRepository.findByActive(true)).thenReturn(List.of(paid, late));
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
        when(memberRepository.findByActive(true)).thenReturn(List.of(m));

        useCase.invoke();

        verify(paymentRepository).existsByMemberAndPeriod(m, previousMonth);
        verify(paymentRepository, never()).existsByMemberAndPeriod(m, YearMonth.now());
    }

    @Test
    void noMembersMeansNoSaves() {
        when(memberRepository.findByActive(true)).thenReturn(List.of());

        useCase.invoke();

        verify(memberRepository, never()).save(any());
    }

    @Test
    void runningTwiceForTheSameMonthIncrementsOnlyOnce() {
        Member late = member("late", 0);
        when(memberRepository.findByActive(true)).thenReturn(List.of(late));

        useCase.invoke();
        useCase.invoke();

        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(1);
        verify(memberRepository).save(late);
    }

    @Test
    void onlyActiveMembersAreLoaded() {
        Member inactive = member("inactive", 0);
        inactive.setStatus(MemberStatus.INACTIVE);
        when(memberRepository.findByActive(true)).thenReturn(List.of());

        useCase.invoke();

        verify(memberRepository, never()).findAll();
        assertThat(inactive.getConsecutiveMonthsMissed()).isZero();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void memberWhoJoinedAfterTheEndOfTheCountedMonthIsSkipped() {
        Member newcomer = member("newcomer", 0);
        newcomer.setJoinDate(previousMonth.plusMonths(1).atDay(1));
        when(memberRepository.findByActive(true)).thenReturn(List.of(newcomer));

        useCase.invoke();

        assertThat(newcomer.getConsecutiveMonthsMissed()).isZero();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void memberWhoJoinedOnTheLastDayOfTheCountedMonthIsCounted() {
        Member lastDay = member("lastDay", 0);
        lastDay.setJoinDate(previousMonth.atEndOfMonth());
        when(memberRepository.findByActive(true)).thenReturn(List.of(lastDay));

        useCase.invoke();

        assertThat(lastDay.getConsecutiveMonthsMissed()).isEqualTo(1);
    }

    @Test
    void memberWithoutJoinDateIsCounted() {
        Member unknown = member("unknown", 0);
        unknown.setJoinDate(null);
        when(memberRepository.findByActive(true)).thenReturn(List.of(unknown));

        useCase.invoke();

        assertThat(unknown.getConsecutiveMonthsMissed()).isEqualTo(1);
        verify(memberRepository).save(unknown);
    }

    @Test
    void savesNothingWhenTheMonthWasAlreadyCounted() {
        Member counted = member("counted", 1);
        counted.setLastMissedCountMonth(previousMonth);
        when(memberRepository.findByActive(true)).thenReturn(List.of(counted));

        useCase.invoke();

        assertThat(counted.getConsecutiveMonthsMissed()).isEqualTo(1);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void recordsWhichMonthWasCounted() {
        Member late = member("late", 0);
        when(memberRepository.findByActive(true)).thenReturn(List.of(late));

        useCase.invoke();

        assertThat(late.getLastMissedCountMonth()).isEqualTo(previousMonth);
    }

    @Test
    void consecutiveMonthsRaiseTheCounterOneThenTwo() {
        Member late = member("late", 0);
        when(memberRepository.findByActive(true)).thenReturn(List.of(late));
        HasPaymentForMonthUseCase hasPayment = new HasPaymentForMonthUseCase(paymentRepository);

        new UpdateMissingPaymentCountersUseCase(memberRepository, hasPayment, clockAt("2026-10-01")).invoke();
        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(1);
        assertThat(late.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));

        new UpdateMissingPaymentCountersUseCase(memberRepository, hasPayment, clockAt("2026-11-01")).invoke();
        assertThat(late.getConsecutiveMonthsMissed()).isEqualTo(2);
        assertThat(late.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 10));
    }
}
