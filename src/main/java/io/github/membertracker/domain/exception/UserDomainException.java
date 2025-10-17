package io.github.membertracker.domain.exception;

/**
 * Domain exception for User entity violations.
 * Covers business rule violations related to user authentication, authorization, and account management.
 */
public class UserDomainException extends DomainException {
    
    // Error codes for different types of user domain violations
    public static final String USERNAME_ALREADY_EXISTS = "USER_001";
    public static final String EMAIL_ALREADY_EXISTS = "USER_002";
    public static final String INVALID_PASSWORD = "USER_003";
    public static final String WEAK_PASSWORD = "USER_004";
    public static final String USER_NOT_FOUND = "USER_005";
    public static final String USER_ALREADY_DISABLED = "USER_006";
    public static final String USER_ALREADY_ENABLED = "USER_007";
    public static final String INVALID_USER_DATA = "USER_008";
    public static final String ACCOUNT_LOCKED = "USER_009";
    public static final String CREDENTIALS_EXPIRED = "USER_010";

    public UserDomainException(String message, String errorCode) {
        super(message, errorCode, "User");
    }

    public UserDomainException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, "User", cause);
    }

    // Factory methods for common user domain violations
    public static UserDomainException emailAlreadyExists(String email) {
        return new UserDomainException(
            String.format("Email '%s' already exists", email),
            EMAIL_ALREADY_EXISTS
        );
    }

    public static UserDomainException invalidPassword() {
        return new UserDomainException(
            "Invalid password provided",
            INVALID_PASSWORD
        );
    }

    public static UserDomainException weakPassword(String requirements) {
        return new UserDomainException(
            String.format("Password does not meet strength requirements: %s", requirements),
            WEAK_PASSWORD
        );
    }

    public static UserDomainException userNotFound(Long userId) {
        return new UserDomainException(
            String.format("User with ID %d not found", userId),
            USER_NOT_FOUND
        );
    }

    public static UserDomainException userNotFound(String email) {
        return new UserDomainException(
            String.format("User with email '%s' not found", email),
            USER_NOT_FOUND
        );
    }

    public static UserDomainException userAlreadyDisabled(String email) {
        return new UserDomainException(
            String.format("User '%s' is already disabled", email),
            USER_ALREADY_DISABLED
        );
    }

    public static UserDomainException userAlreadyEnabled(String email) {
        return new UserDomainException(
            String.format("User '%s' is already enabled", email),
            USER_ALREADY_ENABLED
        );
    }

    public static UserDomainException invalidUserData(String field, String value) {
        return new UserDomainException(
            String.format("Invalid user data: %s '%s' is not valid", field, value),
            INVALID_USER_DATA
        );
    }

    public static UserDomainException accountLocked(String email) {
        return new UserDomainException(
            String.format("Account for user '%s' is locked", email),
            ACCOUNT_LOCKED
        );
    }

    public static UserDomainException credentialsExpired(String email) {
        return new UserDomainException(
            String.format("Credentials for user '%s' have expired", email),
            CREDENTIALS_EXPIRED
        );
    }
}