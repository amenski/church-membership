package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.util.List;

public class GetRecentPaymentsUseCase {

    private final PaymentRepository paymentRepository;

    public GetRecentPaymentsUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** The newest payments by payment date. */
    public List<Payment> invoke(int limit) {
        return paymentRepository.findRecent(limit);
    }
}
