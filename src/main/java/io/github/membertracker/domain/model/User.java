package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.valueobject.Email;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class User implements UserDetails {

    private Long id;
    private Email email;
    private String password;
    private UserRole role;
    private boolean enabled;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastPasswordChange;
    private int failedLoginAttempts;

    // Profile fields
    private String firstName;
    private String lastName;
    private String phone;
    private String bio;

    // Password validation pattern
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    public User() {
    }

    public User(Email email, String password) {
        this(email, password, UserRole.USER);
    }

    public User(Email email, String password, UserRole role) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastPasswordChange = LocalDateTime.now();
        this.failedLoginAttempts = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public String getEmailValue() {
        return email != null ? email.getValue() : null;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return this.email.getValue();
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastPasswordChange() {
        return lastPasswordChange;
    }

    public void setLastPasswordChange(LocalDateTime lastPasswordChange) {
        this.lastPasswordChange = lastPasswordChange;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    // Business Logic Methods

    /**
     * Validates if a password meets the strength requirements.
     * Throws UserDomainException if password is weak.
     */
    public static void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw UserDomainException.weakPassword(
                "Password must be at least 8 characters long and contain at least one uppercase letter, " +
                "one lowercase letter, one digit, and one special character (@$!%*?&)"
            );
        }
    }

    /**
     * Changes the user's password after validating strength.
     * Updates the last password change timestamp.
     */
    public void changePassword(String newPassword) {
        validatePasswordStrength(newPassword);
        this.password = newPassword;
        this.lastPasswordChange = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.failedLoginAttempts = 0; // Reset failed attempts on password change
    }

    /**
     * Records a failed login attempt and locks account if threshold is reached.
     */
    public void recordFailedLoginAttempt() {
        this.failedLoginAttempts++;
        this.updatedAt = LocalDateTime.now();
        
        if (this.failedLoginAttempts >= 5) {
            this.accountNonLocked = false;
        }
    }

    /**
     * Resets failed login attempts and unlocks account.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountNonLocked = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Enables the user account.
     */
    public void enable() {
        if (this.enabled) {
            throw UserDomainException.userAlreadyEnabled(this.email.getValue());
        }
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Disables the user account.
     */
    public void disable() {
        if (!this.enabled) {
            throw UserDomainException.userAlreadyDisabled(this.email.getValue());
        }
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Promotes the user to a higher role.
     */
    public void promoteToRole(UserRole newRole) {
        if (this.role == newRole) {
            throw UserDomainException.invalidUserData("role", "User already has role: " + newRole);
        }
        
        // Validate role hierarchy
        if (newRole == UserRole.USER && this.role != null) {
            throw UserDomainException.invalidUserData("role", "Cannot demote user to USER role");
        }
        
        this.role = newRole;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the user has admin privileges.
     */
    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    /**
     * Checks if the user has manager or admin privileges.
     */
    public boolean isManagerOrAdmin() {
        return this.role == UserRole.MANAGER || this.role == UserRole.ADMIN;
    }

    /**
     * Checks if the user's password is expired (older than 90 days).
     */
    public boolean isPasswordExpired() {
        return lastPasswordChange != null &&
               lastPasswordChange.isBefore(LocalDateTime.now().minusDays(90));
    }

    /**
     * Checks if the account is locked due to too many failed login attempts.
     */
    public boolean isAccountLocked() {
        return !accountNonLocked;
    }

    /**
     * Updates the user's profile information.
     */
    public void updateProfile(String firstName, String lastName, String phone, String bio) {
        if (firstName != null && !firstName.trim().isEmpty()) {
            this.firstName = firstName.trim();
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            this.lastName = lastName.trim();
        }
        this.phone = phone != null ? phone.trim() : null;
        this.bio = bio != null ? bio.trim() : null;
        this.updatedAt = LocalDateTime.now();
    }

    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role != null ? role.toAuthority() : UserRole.USER.toAuthority()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired; // && !isPasswordExpired();
    }
}