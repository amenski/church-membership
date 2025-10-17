package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.util.Optional;

public class GetPaymentByIdUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentByIdUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Retrieves a payment by its ID.
     *
     * @param id the ID of the payment to retrieve
     * @return an Optional containing the payment if found, or empty if not found
     */
    public Optional<Payment> invoke(Long id) {
        return paymentRepository.findById(id);
    }
}