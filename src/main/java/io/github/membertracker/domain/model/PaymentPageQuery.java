package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.enumeration.PaymentSortField;

/**
 * Which page of payments to read. {@code search} (never null, already trimmed, may be empty) matches the member's name
 * as a case-insensitive substring and the receipt number ("R-000141", or its digits); {@code method} is null for any
 * method. Ties on the sort field always fall back to the newest id first.
 */
public record PaymentPageQuery(int page, int size, String search, PaymentMethod method,
                               PaymentSortField sortField, boolean ascending) {
}
