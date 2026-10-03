package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.exception.PaymentDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
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

class RecordPaymentUseCaseTest {

    private PaymentRepository paymentRepository;
    private MemberRepository memberRepository;
    private RecordPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        memberRepository = mock(MemberRepository.class);
        useCase = new RecordPaymentUseCase(paymentRepository, memberRepository);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Member member(long id) {
        Member m = new Member("Alice", "alice@example.com", "+1234567890");
        m.setId(id);
        return m;
    }

    private Payment payment(Member m, YearMonth period, Double amount) {
        return new Payment(m, period, amount, PaymentMethod.CASH);
    }

    @Test
    void currentPeriodPaymentIsSavedAndResetsMissedCounter() {
        Member m = member(1L);
        m.setConsecutiveMonthsMissed(2);
        Payment p = payment(m, YearMonth.now(), 25.0);

        Payment result = useCase.invoke(p);

        assertThat(result).isSameAs(p);
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isZero();
        assertThat(saved.getValue().getLastPaymentDate()).isEqualTo(LocalDate.now());
        verify(paymentRepository).save(p);
    }

    @Test
    void pastPeriodPaymentUpdatesLastPaymentDateButKeepsMissedCounter() {
        Member m = member(1L);
        m.setConsecutiveMonthsMissed(2);

        useCase.invoke(payment(m, YearMonth.now().minusMonths(1), 25.0));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isEqualTo(2);
        assertThat(saved.getValue().getLastPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void missingPaymentDateIsFilledWithToday() {
        Payment p = payment(member(1L), YearMonth.now(), 25.0);
        p.setPaymentDate(null);

        Payment result = useCase.invoke(p);

        assertThat(result.getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void zeroAmountIsRejectedAndNothingIsSaved() {
        Payment p = payment(member(1L), YearMonth.now(), 0.0);

        assertThatThrownBy(() -> useCase.invoke(p)).isInstanceOf(PaymentDomainException.class);

        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void nullAmountIsRejected() {
        Payment p = payment(member(1L), YearMonth.now(), null);

        assertThatThrownBy(() -> useCase.invoke(p)).isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void futurePeriodIsRejected() {
        Payment p = payment(member(1L), YearMonth.now().plusMonths(1), 25.0);

        assertThatThrownBy(() -> useCase.invoke(p)).isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void periodOlderThanThreeMonthsIsRejected() {
        Payment p = payment(member(1L), YearMonth.now().minusMonths(4), 25.0);

        assertThatThrownBy(() -> useCase.invoke(p)).isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void nullPeriodIsRejected() {
        Payment p = payment(member(1L), null, 25.0);

        assertThatThrownBy(() -> useCase.invoke(p)).isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @Disabled("AUDIT C2: use case saves the client-supplied member object instead of loading the member by id")
    void savesTheStoredMemberNotTheClientSuppliedOne() {
        Member stored = member(1L);
        stored.setName("Stored Name");
        stored.setActive(true);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(stored));

        Member forged = member(1L);
        forged.setName("Forged Name");
        forged.setActive(false);

        useCase.invoke(payment(forged, YearMonth.now(), 25.0));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Stored Name");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    @Disabled("AUDIT C2: use case saves the client-supplied member object instead of loading the member by id")
    void unknownMemberIdIsRejected() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke(payment(member(99L), YearMonth.now(), 25.0)))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @Disabled("BUG: RecordPaymentUseCase (the live /payments path) does not reject a second payment for the same member and period, unlike ProcessMemberPaymentUseCase")
    void duplicatePaymentForSameMemberAndPeriodIsRejected() {
        Member m = member(1L);
        when(paymentRepository.existsByMemberAndPeriod(m, YearMonth.now())).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke(payment(m, YearMonth.now(), 25.0)))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
    }
}
