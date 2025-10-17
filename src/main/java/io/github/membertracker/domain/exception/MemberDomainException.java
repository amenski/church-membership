package io.github.membertracker.domain.exception;

/**
 * Domain exception for Member entity violations.
 * Covers business rule violations related to member management, payments, and membership status.
 */
public class MemberDomainException extends DomainException {
    
    // Error codes for different types of member domain violations
    public static final String MEMBER_ALREADY_INACTIVE = "MEMBER_001";
    public static final String MEMBER_ALREADY_ACTIVE = "MEMBER_002";
    public static final String INVALID_MEMBER_DATA = "MEMBER_003";
    public static final String DUPLICATE_PAYMENT_FOR_PERIOD = "MEMBER_004";
    public static final String PAYMENT_AMOUNT_INVALID = "MEMBER_005";
    public static final String MEMBER_NOT_FOUND = "MEMBER_006";

    public MemberDomainException(String message, String errorCode) {
        super(message, errorCode, "Member");
    }

    public MemberDomainException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, "Member", cause);
    }

    // Factory methods for common member domain violations
    public static MemberDomainException memberAlreadyInactive(String memberName) {
        return new MemberDomainException(
            String.format("Member '%s' is already inactive", memberName),
            MEMBER_ALREADY_INACTIVE
        );
    }

    public static MemberDomainException memberAlreadyActive(String memberName) {
        return new MemberDomainException(
            String.format("Member '%s' is already active", memberName),
            MEMBER_ALREADY_ACTIVE
        );
    }

    public static MemberDomainException invalidMemberData(String field, String value) {
        return new MemberDomainException(
            String.format("Invalid member data: %s '%s' is not valid", field, value),
            INVALID_MEMBER_DATA
        );
    }

    public static MemberDomainException duplicatePaymentForPeriod(String memberName, String period) {
        return new MemberDomainException(
            String.format("Member '%s' already has a payment recorded for period %s", memberName, period),
            DUPLICATE_PAYMENT_FOR_PERIOD
        );
    }

    public static MemberDomainException paymentAmountInvalid(String memberName, String amount) {
        return new MemberDomainException(
            String.format("Payment amount %s for member '%s' is invalid", amount, memberName),
            PAYMENT_AMOUNT_INVALID
        );
    }

    public static MemberDomainException memberNotFound(Long memberId) {
        return new MemberDomainException(
            String.format("Member with ID %d not found", memberId),
            MEMBER_NOT_FOUND
        );
    }

    public static MemberDomainException memberNotFound(String email) {
        return new MemberDomainException(
            String.format("Member with email %s not found", email),
            MEMBER_NOT_FOUND
        );
    }
}