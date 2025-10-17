package io.github.membertracker.domain.exception;

import java.math.BigDecimal;
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
    public static final String PAYMENT_ALREADY_PROCESSED = "PAYMENT_004";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_005";
    public static final String PAYMENT_DATE_IN_FUTURE = "PAYMENT_006";
    public static final String PAYMENT_PERIOD_IN_FUTURE = "PAYMENT_007";

    public PaymentDomainException(String message, String errorCode) {
        super(message, errorCode, "Payment");
    }

    public PaymentDomainException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, "Payment", cause);
    }

    // Factory methods for common payment domain violations
    public static PaymentDomainException invalidPaymentAmount(BigDecimal amount, BigDecimal minAmount) {
        return new PaymentDomainException(
            String.format("Payment amount %s is invalid. Minimum amount is %s", amount, minAmount),
            INVALID_PAYMENT_AMOUNT
        );
    }

    public static PaymentDomainException invalidPaymentPeriod(YearMonth period) {
        return new PaymentDomainException(
            String.format("Payment period %s is invalid. Cannot process payments for past periods beyond 3 months", period),
            INVALID_PAYMENT_PERIOD
        );
    }

    public static PaymentDomainException paymentMethodNotSupported(String paymentMethod) {
        return new PaymentDomainException(
            String.format("Payment method '%s' is not supported", paymentMethod),
            PAYMENT_METHOD_NOT_SUPPORTED
        );
    }

    public static PaymentDomainException paymentAlreadyProcessed(String memberName, YearMonth period) {
        return new PaymentDomainException(
            String.format("Payment for member '%s' for period %s has already been processed", memberName, period),
            PAYMENT_ALREADY_PROCESSED
        );
    }

    public static PaymentDomainException paymentNotFound(Long paymentId) {
        return new PaymentDomainException(
            String.format("Payment with ID %d not found", paymentId),
            PAYMENT_NOT_FOUND
        );
    }

    public static PaymentDomainException paymentDateInFuture() {
        return new PaymentDomainException(
            "Payment date cannot be in the future",
            PAYMENT_DATE_IN_FUTURE
        );
    }

    public static PaymentDomainException paymentPeriodInFuture(YearMonth period) {
        return new PaymentDomainException(
            String.format("Payment period %s is in the future. Cannot process payments for future periods", period),
            PAYMENT_PERIOD_IN_FUTURE
        );
    }
}