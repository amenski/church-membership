package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.exception.PaymentDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordPaymentUseCaseTest {

    private PaymentRepository paymentRepository;
    private MemberRepository memberRepository;
    private RecordActivityUseCase recordActivity;
    private RecordPaymentUseCase useCase;
    private Member stored;

    @BeforeEach
    void setUp() {
        stored = member(1L);
        paymentRepository = mock(PaymentRepository.class);
        memberRepository = mock(MemberRepository.class);
        recordActivity = mock(RecordActivityUseCase.class);
        useCase = new RecordPaymentUseCase(paymentRepository, memberRepository, recordActivity);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.findById(1L)).thenAnswer(i -> Optional.of(stored));
    }

    private Member member(long id) {
        Member m = new Member("Alice", "alice@example.com", "+1234567890");
        m.setId(id);
        return m;
    }

    @Test
    void currentPeriodPaymentIsSavedAndResetsMissedCounter() {
        stored.setConsecutiveMonthsMissed(2);

        Payment result = useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, "front desk");

        assertThat(result.getMember()).isSameAs(stored);
        assertThat(result.getAmount()).isEqualTo(25.0);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(result.getNotes()).isEqualTo("front desk");
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isZero();
        assertThat(saved.getValue().getLastPaymentDate()).isEqualTo(LocalDate.now());
        verify(paymentRepository).save(result);
    }

    @Test
    void aRecordedPaymentIsLoggedWithAmountPeriodAndNameButNoContactDetails() {
        useCase.invoke(1L, 50.0, PaymentMethod.CASH, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 3), null);

        verify(recordActivity).record(eq(ActivityType.PAYMENT_RECORDED),
                eq("Payment of 50.00 for 2026-10 was recorded for Alice"), eq("PAYMENT"), any());
    }

    @Test
    void missingPeriodDefaultsToCurrentMonth() {
        Payment result = useCase.invoke(1L, 25.0, PaymentMethod.CASH, null, null, null);

        assertThat(result.getPeriod()).isEqualTo(YearMonth.now());
        assertThat(result.getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void pastPeriodPaymentUpdatesLastPaymentDateButKeepsMissedCounter() {
        stored.setConsecutiveMonthsMissed(2);

        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusMonths(1), null, null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isEqualTo(2);
        assertThat(saved.getValue().getLastPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void zeroAmountIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> useCase.invoke(1L, 0.0, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(PaymentDomainException.class);

        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void aRejectedPaymentIsNotLogged() {
        assertThatThrownBy(() -> useCase.invoke(1L, 0.0, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(PaymentDomainException.class);

        verifyNoInteractions(recordActivity);
    }

    @Test
    void nullAmountIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, null, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void futurePeriodIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().plusMonths(1), null, null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void anyPastMonthUpToTenYearsBackIsAccepted() {
        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusMonths(4), null, null);
        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusYears(10), null, null);

        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    void periodElevenYearsBackIsRejectedAsATypo() {
        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusYears(11), null, null))
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("too far back");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void currentMonthIsAcceptedAndNextMonthRejected() {
        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, null);
        verify(paymentRepository).save(any());

        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().plusMonths(1), null, null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, times(1)).save(any());
    }

    @Test
    void paymentForAnInactiveMemberIsRejectedAndNothingIsSaved() {
        stored.setActive(false);

        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(MemberDomainException.class)
                .hasMessage("Member 'Alice' is inactive. Reactivate the member before recording a payment.")
                .extracting("errorCode").isEqualTo(MemberDomainException.MEMBER_INACTIVE);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void backDatedPaymentIsSavedWithItsDate() {
        LocalDate paidOn = LocalDate.of(2024, 3, 10);

        Payment result = useCase.invoke(1L, 50.0, PaymentMethod.CHECK, YearMonth.of(2024, 3), paidOn, null);

        assertThat(result.getPaymentDate()).isEqualTo(paidOn);
        assertThat(result.getPeriod()).isEqualTo(YearMonth.of(2024, 3));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentDate()).isEqualTo(paidOn);
        assertThat(stored.getLastPaymentDate()).isEqualTo(paidOn);
    }

    @Test
    void lastPaymentDateKeepsTheLaterDate() {
        stored.setLastPaymentDate(LocalDate.of(2026, 9, 1));

        useCase.invoke(1L, 50.0, PaymentMethod.CASH, YearMonth.of(2024, 3), LocalDate.of(2024, 3, 10), null);

        assertThat(stored.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void paymentDateInTheFutureIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(),
                LocalDate.now().plusDays(1), null))
                .isInstanceOf(PaymentDomainException.class)
                .extracting("errorCode").isEqualTo(PaymentDomainException.PAYMENT_DATE_IN_FUTURE);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void savesTheMemberLoadedFromTheRepository() {
        stored.setName("Stored Name");
        stored.setActive(true);

        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(stored);
        assertThat(saved.getValue().getName()).isEqualTo("Stored Name");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void unknownMemberIdIsRejected() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke(99L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void duplicatePaymentForSameMemberAndPeriodIsRejected() {
        when(paymentRepository.existsByMemberAndPeriod(stored, YearMonth.now())).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null, null))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
    }
}
