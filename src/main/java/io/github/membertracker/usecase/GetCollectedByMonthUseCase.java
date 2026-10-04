package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class GetCollectedByMonthUseCase {

    /** One bar of the dashboard chart: the billing month as {@code yyyy-MM} and what was collected for it. */
    public record MonthlyCollected(String month, double amount) {
    }

    private final PaymentRepository paymentRepository;

    public GetCollectedByMonthUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * The last {@code months} billing months ending with the current one, oldest first. A month with no
     * payments is listed with 0, so the chart never has a gap.
     */
    public List<MonthlyCollected> invoke(int months) {
        YearMonth current = YearMonth.now();
        List<MonthlyCollected> collected = new ArrayList<>();
        for (int back = months - 1; back >= 0; back--) {
            YearMonth period = current.minusMonths(back);
            collected.add(new MonthlyCollected(period.toString(), paymentRepository.sumAmountByPeriod(period)));
        }
        return collected;
    }
}
