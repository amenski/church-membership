package io.github.membertracker.domain.exception;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Domain exception for Payment entity violations.
 * Covers business rule violations related to payment processing, amounts, and periods.
 */
public class PaymentDomainException extends DomainException {
    
    // Error codes for different types of payment domain violations
    public static final String INVALID_PAYMENT_AMOUNT = "PAYMENT_001";
    public static final String INVALID_PAYMENT_PERIOD = "PAYMENT_002";
    public static final String PAYMENT_METHOD_NOT_SUPPORTED = "PAYMENT_003";
    public static final String PAYMENT_PERIOD_IN_FUTURE = "PAYMENT_007";
    public static final String PAYMENT_DATE_IN_FUTURE = "PAYMENT_008";

    public PaymentDomainException(String message, String errorCode) {
        super(message, errorCode, "Payment");
    }

    public PaymentDomainException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, "Payment", cause);
    }

    // Factory methods for common payment domain violations
    public static PaymentDomainException invalidPaymentAmount(Double amount) {
        return new PaymentDomainException(
            String.format("Payment amount %s is invalid. Amount must be greater than 0", amount),
            INVALID_PAYMENT_AMOUNT
        );
    }

    public static PaymentDomainException invalidPaymentPeriod(YearMonth period) {
        return new PaymentDomainException(
            "A payment period (a month, YYYY-MM) is required",
            INVALID_PAYMENT_PERIOD
        );
    }

    public static PaymentDomainException paymentPeriodTooOld(YearMonth period, YearMonth earliest) {
        return new PaymentDomainException(
            String.format("Payment period %s is too far back and looks like a typing mistake. The earliest month accepted is %s",
                period, earliest),
            INVALID_PAYMENT_PERIOD
        );
    }

    public static PaymentDomainException paymentDateInFuture(LocalDate paymentDate) {
        return new PaymentDomainException(
            String.format("Payment date %s is in the future. Use today or an earlier date", paymentDate),
            PAYMENT_DATE_IN_FUTURE
        );
    }

    public static PaymentDomainException paymentMethodNotSupported(String paymentMethod) {
        return new PaymentDomainException(
            String.format("Payment method '%s' is not supported", paymentMethod),
            PAYMENT_METHOD_NOT_SUPPORTED
        );
    }

    public static PaymentDomainException paymentPeriodInFuture(YearMonth period) {
        return new PaymentDomainException(
            String.format("Payment period %s is in the future. Cannot process payments for future periods", period),
            PAYMENT_PERIOD_IN_FUTURE
        );
    }
}