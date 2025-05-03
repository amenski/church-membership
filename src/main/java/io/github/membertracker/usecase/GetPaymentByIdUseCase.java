package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
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
    @Transactional(readOnly = true)
    public Optional<Payment> invoke(Long id) {
        return paymentRepository.findById(id);
    }
}