package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.PaymentSummary;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.YearMonth;

public class GetPaymentSummaryUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentSummaryUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** This month (payments whose billing period is the current month), all time and the average: one aggregate statement. */
    public PaymentSummary invoke() {
        return paymentRepository.summarize(YearMonth.now());
    }
}
