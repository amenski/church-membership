package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public class GetPaidMonthsUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaidMonthsUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * For each member with a payment in the last {@code months} months (this month included), the distinct billing
     * months that were paid, oldest first. Members with none are left out. One grouped statement.
     */
    public Map<Long, List<YearMonth>> invoke(int months) {
        YearMonth current = YearMonth.now();
        return paymentRepository.findPaidMonthsBetween(current.minusMonths(months - 1L), current);
    }
}
