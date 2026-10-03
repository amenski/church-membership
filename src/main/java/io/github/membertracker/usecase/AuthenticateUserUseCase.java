package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Checks an email and password.
 *
 * <p>An unknown email, a wrong password and a locked account all end in the same
 * {@link UserDomainException#invalidCredentials()}, and a locked account never has its password
 * checked, so a lock cannot be used to probe passwords. The attempt counter, the reset and the lock
 * are single-row SQL updates, never a save of the whole user: a slow BCrypt check cannot overwrite
 * a concurrent password change.
 */
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private String dummyHash;

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, Clock.systemDefaultZone());
    }

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public User invoke(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Same BCrypt cost as for a real account, so timing does not reveal whether the email exists.
            passwordEncoder.matches(password, dummyHash());
            throw UserDomainException.invalidCredentials();
        }

        LocalDateTime now = LocalDateTime.now(clock);

        if (user.isLockExpired(now)) {
            userRepository.resetFailedLogins(user.getId());
            user.resetFailedLoginAttempts();
        }

        if (user.isLocked(now)) {
            // Do not look at the password and do not reveal the lock; spend the same time as a real check.
            passwordEncoder.matches(password, dummyHash());
            throw UserDomainException.invalidCredentials();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            userRepository.recordFailedLogin(user.getId(), User.MAX_FAILED_LOGIN_ATTEMPTS, now.plus(User.LOCK_DURATION));
            throw UserDomainException.invalidCredentials();
        }

        if (!user.isEnabled()) {
            throw UserDomainException.userAlreadyDisabled(email);
        }

        if (!user.isCredentialsNonExpired()) {
            throw UserDomainException.credentialsExpired(email);
        }

        userRepository.resetFailedLogins(user.getId());
        user.resetFailedLoginAttempts();

        return user;
    }

    private synchronized String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password");
        }
        return dummyHash;
    }
}
