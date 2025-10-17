package io.github.membertracker.domain.valueobject;

import io.github.membertracker.domain.exception.MemberDomainException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a phone number.
 * Encapsulates phone number validation logic and ensures type safety.
 */
public final class PhoneNumber {
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[0-9\\s\\-\\(\\)]{10,}$"
    );
    
    private final String value;

    private PhoneNumber(String value) {
        this.value = value;
    }

    /**
     * Factory method to create a PhoneNumber value object.
     * Validates the phone number format and throws domain exception if invalid.
     */
    public static PhoneNumber of(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw MemberDomainException.invalidMemberData("phone", phoneNumber);
        }
        
        String normalizedPhone = normalizePhoneNumber(phoneNumber.trim());
        
        if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            throw MemberDomainException.invalidMemberData("phone", phoneNumber);
        }
        
        return new PhoneNumber(normalizedPhone);
    }

    /**
     * Normalizes the phone number by removing common formatting characters.
     */
    private static String normalizePhoneNumber(String phoneNumber) {
        // Remove spaces, dashes, parentheses
        return phoneNumber.replaceAll("[\\s\\-\\(\\)]", "");
    }

    /**
     * Returns the string representation of the phone number.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns a formatted version of the phone number for display.
     */
    public String toFormattedString() {
        if (value.length() == 10) {
            // Format as (XXX) XXX-XXXX
            return String.format("(%s) %s-%s", 
                value.substring(0, 3),
                value.substring(3, 6),
                value.substring(6));
        } else if (value.length() > 10 && value.startsWith("+")) {
            // International format
            return value;
        } else {
            return value;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneNumber that = (PhoneNumber) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return toFormattedString();
    }
}