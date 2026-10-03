package io.github.membertracker.domain.enumeration;

import io.github.membertracker.domain.exception.PaymentDomainException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration representing supported payment methods.
 * Provides type safety for payment method handling and validation.
 */
public enum PaymentMethod {
    CASH("CASH"),
    BANK_TRANSFER("BANK_TRANSFER"),
    CREDIT_CARD("CREDIT_CARD"),
    DEBIT_CARD("DEBIT_CARD"),
    MOBILE_PAYMENT("MOBILE_PAYMENT"),
    ONLINE_PAYMENT("ONLINE_PAYMENT"),
    CHECK("CHECK");

    private final String code;

    private static final Map<String, PaymentMethod> BY_CODE = Arrays.stream(values())
        .collect(Collectors.toMap(PaymentMethod::getCode, Function.identity()));

    PaymentMethod(String code) {
        this.code = code;
    }

    /**
     * Returns the code representation of the payment method.
     */
    public String getCode() {
        return code;
    }

    /**
     * Converts a string code to a PaymentMethod enum.
     * Throws domain exception if the code is not supported.
     */
    public static PaymentMethod fromCode(String code) {
        if (code == null) {
            throw PaymentDomainException.paymentMethodNotSupported("null");
        }
        
        PaymentMethod method = BY_CODE.get(code.toUpperCase());
        if (method == null) {
            throw PaymentDomainException.paymentMethodNotSupported(code);
        }
        
        return method;
    }

    @Override
    public String toString() {
        return code;
    }
}