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
    CASH("CASH", "Cash"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer"),
    CREDIT_CARD("CREDIT_CARD", "Credit Card"),
    DEBIT_CARD("DEBIT_CARD", "Debit Card"),
    MOBILE_PAYMENT("MOBILE_PAYMENT", "Mobile Payment"),
    ONLINE_PAYMENT("ONLINE_PAYMENT", "Online Payment");

    private final String code;
    private final String displayName;

    private static final Map<String, PaymentMethod> BY_CODE = Arrays.stream(values())
        .collect(Collectors.toMap(PaymentMethod::getCode, Function.identity()));

    PaymentMethod(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Returns the code representation of the payment method.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the display name of the payment method.
     */
    public String getDisplayName() {
        return displayName;
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

    /**
     * Checks if a given code represents a valid payment method.
     */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        return BY_CODE.containsKey(code.toUpperCase());
    }

    /**
     * Returns all payment methods as a map of code to display name.
     */
    public static Map<String, String> getAllAsMap() {
        return Arrays.stream(values())
            .collect(Collectors.toMap(PaymentMethod::getCode, PaymentMethod::getDisplayName));
    }

    @Override
    public String toString() {
        return code;
    }
}