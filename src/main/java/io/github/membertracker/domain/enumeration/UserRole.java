package io.github.membertracker.domain.enumeration;

/**
 * Enumeration representing user roles in the system.
 * Provides type-safe role management and validation.
 */
public enum UserRole {
    MEMBER("MEMBER"),
    VOLUNTEER("VOLUNTEER"),
    STAFF("STAFF"),
    ADMIN("ADMIN");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    /**
     * Returns the code representation of the user role.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the Spring Security authority representation for this role.
     */
    public String toAuthority() {
        return "ROLE_" + code;
    }

    @Override
    public String toString() {
        return code;
    }
}