package io.github.membertracker.domain.enumeration;

import io.github.membertracker.domain.exception.UserDomainException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration representing user roles in the system.
 * Provides type-safe role management and validation.
 */
public enum UserRole {
    MEMBER("MEMBER", "Member", "Basic authenticated member with limited access"),
    VOLUNTEER("VOLUNTEER", "Volunteer", "Volunteer with limited operational access"),
    STAFF("STAFF", "Staff", "Staff member who can manage members and communications"),
    ADMIN("ADMIN", "Administrator", "Full system access with administrative privileges");

    private final String code;
    private final String displayName;
    private final String description;

    private static final Map<String, UserRole> BY_CODE = Arrays.stream(values())
        .collect(Collectors.toMap(UserRole::getCode, Function.identity()));

    UserRole(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the code representation of the user role.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the display name of the user role.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the description of the user role.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Converts a string code to a UserRole enum.
     * Throws domain exception if the code is not supported.
     */
    public static UserRole fromCode(String code) {
        if (code == null) {
            throw UserDomainException.invalidUserData("role", "null");
        }
        
        UserRole role = BY_CODE.get(code.toUpperCase());
        if (role == null) {
            throw UserDomainException.invalidUserData("role", code);
        }
        
        return role;
    }

    /**
     * Checks if a given code represents a valid user role.
     */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        return BY_CODE.containsKey(code.toUpperCase());
    }

    /**
     * Returns all user roles as a map of code to display name.
     */
    public static Map<String, String> getAllAsMap() {
        return Arrays.stream(values())
            .collect(Collectors.toMap(UserRole::getCode, UserRole::getDisplayName));
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