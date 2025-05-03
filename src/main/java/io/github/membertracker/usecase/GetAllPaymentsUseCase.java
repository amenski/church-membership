package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
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
    @Transactional(readOnly = true)
    public List<Payment> invoke() {
        return paymentRepository.findAll();
    }
}