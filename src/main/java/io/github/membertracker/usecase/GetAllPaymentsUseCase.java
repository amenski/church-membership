package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.util.List;

public class GetAllPaymentsUseCase {

    private final PaymentRepository paymentRepository;

    public GetAllPaymentsUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Retrieves all payments from the database.
     *
     * @return a list of all payments
     */
    public List<Payment> invoke() {
        return paymentRepository.findAll();
    }
}