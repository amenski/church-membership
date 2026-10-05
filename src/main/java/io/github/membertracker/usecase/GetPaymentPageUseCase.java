package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.PageResult;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.model.PaymentPageQuery;
import io.github.membertracker.domain.repository.PaymentRepository;

public class GetPaymentPageUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentPageUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** One page of payments, filtered and sorted as the query says: one count and one page statement. */
    public PageResult<Payment> invoke(PaymentPageQuery query) {
        return paymentRepository.findPage(query);
    }
}
