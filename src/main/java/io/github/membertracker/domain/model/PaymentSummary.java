package io.github.membertracker.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The three figures above the payment history: what was paid for the current billing month, what was ever paid, the
 * average payment, and how many payments there are. Amounts are rounded to cents.
 */
public record PaymentSummary(double thisMonth, double allTime, double average, long count) {

    public static PaymentSummary of(double thisMonth, double allTime, long count) {
        double average = count == 0 ? 0.0 : allTime / count;
        return new PaymentSummary(cents(thisMonth), cents(allTime), cents(average), count);
    }

    private static double cents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
