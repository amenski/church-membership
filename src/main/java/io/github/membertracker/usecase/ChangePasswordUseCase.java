package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Changes a password after checking the current one. A wrong current password counts toward the same
 * lock as a wrong sign-in password. There is deliberately no class-level transaction: the failed-attempt
 * count must survive the exception thrown right after it, and each repository update is its own
 * transaction.
 */
@Service
public class ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ChangePasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User execute(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserDomainException.userNotFound(userId));

        LocalDateTime now = LocalDateTime.now();

        if (user.isLockExpired(now)) {
            userRepository.resetFailedLogins(userId);
            user.resetFailedLoginAttempts();
        }

        if (user.isLocked(now)) {
            throw UserDomainException.invalidPassword();
        }

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            userRepository.recordFailedLogin(userId, User.MAX_FAILED_LOGIN_ATTEMPTS, now.plus(User.LOCK_DURATION));
            throw UserDomainException.invalidPassword();
        }

        // Validate new password strength
        User.validatePasswordStrength(newPassword);

        // Update password. Whole seconds: token issue times are whole seconds and are compared with this value.
        user.changePassword(passwordEncoder.encode(newPassword));
        user.setLastPasswordChange(user.getLastPasswordChange().truncatedTo(ChronoUnit.SECONDS));
        userRepository.updatePassword(userId, user.getPassword(), user.getLastPasswordChange());

        return user;
    }
}
