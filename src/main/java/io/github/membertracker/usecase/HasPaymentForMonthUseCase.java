package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.YearMonth;

public class HasPaymentForMonthUseCase {

    private final PaymentRepository paymentRepository;

    public HasPaymentForMonthUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public boolean invoke(Member member, YearMonth month) {
        return paymentRepository.existsByMemberAndPeriod(member, month);
    }
}