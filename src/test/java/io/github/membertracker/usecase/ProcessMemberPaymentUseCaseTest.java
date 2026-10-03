package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.exception.PaymentDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.policy.DefaultMembershipPolicy;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessMemberPaymentUseCaseTest {

    private MemberRepository memberRepository;
    private PaymentRepository paymentRepository;
    private ProcessMemberPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        useCase = new ProcessMemberPaymentUseCase(memberRepository, paymentRepository, new DefaultMembershipPolicy());
    }

    private Member member(long id, boolean active, int missed) {
        Member m = new Member("Bob", "bob@example.com", "+1234567890");
        m.setId(id);
        m.setActive(active);
        m.setConsecutiveMonthsMissed(missed);
        return m;
    }

    @Test
    void validPaymentIsSavedAndMemberCounterResets() {
        Member m = member(1L, true, 2);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        Payment result = useCase.invoke(1L, 30.0, YearMonth.now(), "cash", "note");

        assertThat(result.getAmount()).isEqualTo(30.0);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(result.getNotes()).isEqualTo("note");
        assertThat(result.getPaymentDate()).isEqualTo(LocalDate.now());
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getMember()).isSameAs(m);
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isZero();
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void unknownMemberThrowsNotFound() {
        when(memberRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke(5L, 30.0, YearMonth.now(), "CASH", null))
                .isInstanceOfSatisfying(MemberDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberDomainException.MEMBER_NOT_FOUND));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void inactiveMemberCannotPay() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, false, 0)));

        assertThatThrownBy(() -> useCase.invoke(1L, 30.0, YearMonth.now(), "CASH", null))
                .isInstanceOfSatisfying(MemberDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberDomainException.MEMBER_ALREADY_INACTIVE));
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void duplicatePaymentForPeriodIsRejected() {
        Member m = member(1L, true, 0);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));
        when(paymentRepository.existsByMemberAndPeriod(m, YearMonth.now())).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke(1L, 30.0, YearMonth.now(), "CASH", null))
                .isInstanceOfSatisfying(MemberDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberDomainException.DUPLICATE_PAYMENT_FOR_PERIOD));
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void unsupportedPaymentMethodIsRejected() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, true, 0)));

        assertThatThrownBy(() -> useCase.invoke(1L, 30.0, YearMonth.now(), "BITCOIN", null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void nonPositiveAmountIsRejected() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, true, 0)));

        assertThatThrownBy(() -> useCase.invoke(1L, -5.0, YearMonth.now(), "CASH", null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void futurePeriodIsRejected() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, true, 0)));

        assertThatThrownBy(() -> useCase.invoke(1L, 30.0, YearMonth.now().plusMonths(1), "CASH", null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void periodOlderThanThreeMonthsIsRejected() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, true, 0)));

        assertThatThrownBy(() -> useCase.invoke(1L, 30.0, YearMonth.now().minusMonths(4), "CASH", null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void memberWithThreeMissedMonthsIsDeactivatedWhenPayingAnOldPeriod() {
        Member m = member(1L, true, 3);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        useCase.invoke(1L, 30.0, YearMonth.now().minusMonths(1), "CASH", null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isEqualTo(3);
    }

    @Test
    void reactivationActivatesRecentlyLapsedMemberAndRecordsPayment() {
        Member m = member(1L, false, 3);
        m.setLastPaymentDate(LocalDate.now().minusDays(10));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        Payment result = useCase.processPaymentWithReactivation(1L, 30.0, YearMonth.now(), "CASH");

        assertThat(result.getNotes()).isEqualTo("Payment with reactivation");
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isZero();
    }

    @Test
    void reactivationRefusedWhenGracePeriodExpired() {
        Member m = member(1L, false, 3);
        m.setLastPaymentDate(LocalDate.now().minusDays(90));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> useCase.processPaymentWithReactivation(1L, 30.0, YearMonth.now(), "CASH"))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @Disabled("BUG: processPaymentWithReactivation activates a member it never saves, then invoke() reloads a fresh copy (JPA mapper returns a new object each call) which is still inactive, so reactivation always fails")
    void reactivationWorksWhenRepositoryReturnsFreshCopiesOnEachLookup() {
        when(memberRepository.findById(1L)).thenAnswer(i -> {
            Member fresh = member(1L, false, 3);
            fresh.setLastPaymentDate(LocalDate.now().minusDays(10));
            return Optional.of(fresh);
        });

        Payment result = useCase.processPaymentWithReactivation(1L, 30.0, YearMonth.now(), "CASH");

        assertThat(result).isNotNull();
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void paymentStatusReportsOverdueAndStanding() {
        Member m = member(1L, true, 1);
        m.setLastPaymentDate(LocalDate.now().minusDays(5));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        ProcessMemberPaymentUseCase.MemberPaymentStatus status = useCase.getMemberPaymentStatus(1L);

        assertThat(status.getMemberId()).isEqualTo(1L);
        assertThat(status.isPaymentOverdue()).isTrue();
        assertThat(status.isInGoodStanding()).isTrue();
        assertThat(status.getConsecutiveMonthsMissed()).isEqualTo(1);
        assertThat(status.getDaysUntilPaymentDue())
                .isEqualTo(YearMonth.now().atEndOfMonth().getDayOfMonth() - LocalDate.now().getDayOfMonth());
    }

    @Test
    void paymentStatusForUnknownMemberThrows() {
        when(memberRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getMemberPaymentStatus(9L)).isInstanceOf(MemberDomainException.class);
    }
}
