package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.valueobject.Email;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private LocalDateTime lockedUntil;

    /** Failed sign-ins (or password changes with a wrong current password) that lock the account. */
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    /** How long a failure-triggered lock lasts. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Profile fields
    private String firstName;
    private String lastName;
    private String phone;
    private String bio;

    // Password rule: 8 characters minimum, 72 UTF-8 bytes maximum (BCrypt's limit)
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    // A special character is anything that is not a letter, a digit or whitespace
    private static final Pattern PASSWORD_SPECIAL = Pattern.compile(
        "[^\\p{L}\\p{N}\\s]", Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern PASSWORD_LOWER = Pattern.compile("\\p{Ll}");
    private static final Pattern PASSWORD_UPPER = Pattern.compile("\\p{Lu}");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile("\\p{Nd}");

    public User() {
    }

    public User(Email email, String password) {
        this(email, password, UserRole.MEMBER);
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

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
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
        boolean valid = password != null
            && password.length() >= PASSWORD_MIN_LENGTH
            && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES
            && PASSWORD_LOWER.matcher(password).find()
            && PASSWORD_UPPER.matcher(password).find()
            && PASSWORD_DIGIT.matcher(password).find()
            && PASSWORD_SPECIAL.matcher(password).find();
        if (!valid) {
            throw UserDomainException.weakPassword(
                "Password must be 8 to 72 characters (bytes) and contain an uppercase letter, " +
                "a lowercase letter, a digit and a special character"
            );
        }
    }

    /**
     * Changes the user's password to an already-encoded value.
     * Callers must validate the plain password with {@link #validatePasswordStrength}
     * before encoding it; the encoded value is not checked here.
     * Updates the last password change timestamp.
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.lastPasswordChange = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.failedLoginAttempts = 0; // Reset failed attempts on password change
    }

    /**
     * Records a failed login attempt and locks account if threshold is reached.
     */
    public void recordFailedLoginAttempt() {
        recordFailedLoginAttempt(LocalDateTime.now());
    }

    public void recordFailedLoginAttempt(LocalDateTime now) {
        this.failedLoginAttempts++;
        this.updatedAt = now;

        if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            this.accountNonLocked = false;
            this.lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    /**
     * Resets failed login attempts and unlocks account.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountNonLocked = true;
        this.lockedUntil = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the account is locked due to too many failed login attempts.
     */
    public boolean isAccountLocked() {
        return isLocked(LocalDateTime.now());
    }

    /**
     * A lock without an expiry (set by an admin or an old row) is permanent; a lock with an expiry
     * lasts until that moment.
     */
    public boolean isLocked(LocalDateTime now) {
        return !accountNonLocked && (lockedUntil == null || lockedUntil.isAfter(now));
    }

    /** True when the account carries a temporary lock whose time has passed and can be cleared. */
    public boolean isLockExpired(LocalDateTime now) {
        return !accountNonLocked && lockedUntil != null && !lockedUntil.isAfter(now);
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
        return List.of(new SimpleGrantedAuthority(role != null ? role.toAuthority() : UserRole.MEMBER.toAuthority()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }
}