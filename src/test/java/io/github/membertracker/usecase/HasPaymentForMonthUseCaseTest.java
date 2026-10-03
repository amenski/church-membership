package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HasPaymentForMonthUseCaseTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final HasPaymentForMonthUseCase useCase = new HasPaymentForMonthUseCase(paymentRepository);
    private final Member member = new Member("Carol", "carol@example.com", "+1234567890");

    @Test
    void trueOnlyForTheMonthThatHasAPayment() {
        YearMonth paid = YearMonth.of(2026, 5);
        when(paymentRepository.existsByMemberAndPeriod(member, paid)).thenReturn(true);

        assertThat(useCase.invoke(member, paid)).isTrue();
        assertThat(useCase.invoke(member, paid.plusMonths(1))).isFalse();
    }
}
