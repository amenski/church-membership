package io.github.membertracker.domain.enumeration;

import java.util.Arrays;
import java.util.Optional;

/** What the paged payment list can be sorted by; {@code parameter} is the name the API accepts. */
public enum PaymentSortField {
    PAYMENT_DATE("paymentDate"),
    PERIOD("period"),
    AMOUNT("amount"),
    MEMBER("member");

    private final String parameter;

    PaymentSortField(String parameter) {
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }

    public static Optional<PaymentSortField> fromParameter(String parameter) {
        return Arrays.stream(values()).filter(field -> field.parameter.equals(parameter)).findFirst();
    }
}
