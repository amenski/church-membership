package io.github.membertracker.domain.valueobject;

import io.github.membertracker.domain.exception.UserDomainException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing an email address.
 * Encapsulates email validation logic and ensures type safety.
 */
public final class Email {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private final String value;

    private Email(String value) {
        this.value = value;
    }

    /**
     * Factory method to create an Email value object.
     * Validates the email format and throws domain exception if invalid.
     */
    public static Email of(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw UserDomainException.invalidUserData("email", email);
        }
        
        String trimmedEmail = email.trim().toLowerCase();
        
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            throw UserDomainException.invalidUserData("email", email);
        }
        
        return new Email(trimmedEmail);
    }

    /**
     * Returns the string representation of the email.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the domain part of the email address.
     */
    public String getDomain() {
        int atIndex = value.indexOf('@');
        return value.substring(atIndex + 1);
    }

    /**
     * Returns the local part of the email address.
     */
    public String getLocalPart() {
        int atIndex = value.indexOf('@');
        return value.substring(0, atIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}