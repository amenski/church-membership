package io.github.membertracker.usecase;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordPaymentUseCaseTest {

    private PaymentRepository paymentRepository;
    private MemberRepository memberRepository;
    private RecordPaymentUseCase useCase;
    private Member stored;

    @BeforeEach
    void setUp() {
        stored = member(1L);
        paymentRepository = mock(PaymentRepository.class);
        memberRepository = mock(MemberRepository.class);
        useCase = new RecordPaymentUseCase(paymentRepository, memberRepository);
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

        Payment result = useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), "front desk");

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
    void missingPeriodDefaultsToCurrentMonth() {
        Payment result = useCase.invoke(1L, 25.0, PaymentMethod.CASH, null, null);

        assertThat(result.getPeriod()).isEqualTo(YearMonth.now());
        assertThat(result.getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void pastPeriodPaymentUpdatesLastPaymentDateButKeepsMissedCounter() {
        stored.setConsecutiveMonthsMissed(2);

        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusMonths(1), null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getConsecutiveMonthsMissed()).isEqualTo(2);
        assertThat(saved.getValue().getLastPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void zeroAmountIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> useCase.invoke(1L, 0.0, PaymentMethod.CASH, YearMonth.now(), null))
                .isInstanceOf(PaymentDomainException.class);

        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void nullAmountIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, null, PaymentMethod.CASH, YearMonth.now(), null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void futurePeriodIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().plusMonths(1), null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void periodOlderThanThreeMonthsIsRejected() {
        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now().minusMonths(4), null))
                .isInstanceOf(PaymentDomainException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void savesTheMemberLoadedFromTheRepository() {
        stored.setName("Stored Name");
        stored.setActive(true);

        useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(stored);
        assertThat(saved.getValue().getName()).isEqualTo("Stored Name");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void unknownMemberIdIsRejected() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke(99L, 25.0, PaymentMethod.CASH, YearMonth.now(), null))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void duplicatePaymentForSameMemberAndPeriodIsRejected() {
        when(paymentRepository.existsByMemberAndPeriod(stored, YearMonth.now())).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke(1L, 25.0, PaymentMethod.CASH, YearMonth.now(), null))
                .isInstanceOf(MemberDomainException.class);
        verify(paymentRepository, never()).save(any());
    }
}
