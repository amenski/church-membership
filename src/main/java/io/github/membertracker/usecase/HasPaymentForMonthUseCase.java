package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class HasPaymentForMonthUseCase {

    private final PaymentRepository paymentRepository;

    public HasPaymentForMonthUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Checks if a member has made a payment for a specific month.
     *
     * @param member the member to check
     * @param month the month to check for payment
     * @return true if the member has made a payment for the specified month, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean invoke(Member member, YearMonth month) {
        return !paymentRepository.findByMemberAndPeriod(member, month).isEmpty();
    }
}