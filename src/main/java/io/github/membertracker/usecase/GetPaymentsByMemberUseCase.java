package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetPaymentsByMemberUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentsByMemberUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Retrieves all payments made by a specific member.
     *
     * @param member the member whose payments to retrieve
     * @return a list of payments made by the member
     */
    @Transactional(readOnly = true)
    public List<Payment> invoke(Member member) {
        return paymentRepository.findByMember(member);
    }
}