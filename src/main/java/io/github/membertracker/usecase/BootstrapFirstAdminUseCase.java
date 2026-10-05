package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.temporal.ChronoUnit;

/**
 * Creates the first administrator of an empty installation: production starts with no users (the sample users are
 * dev-only), and there is no registration, so this is the way in. It runs at startup with the values of
 * BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, and only while the users table is empty, so leaving the
 * variables in place after the first start is harmless. The email and the password follow the same rules as every
 * other account. An invalid value stops the start with a message that names the variable and never repeats the
 * password.
 */
public class BootstrapFirstAdminUseCase {

    static final String EMAIL_VARIABLE = "BOOTSTRAP_ADMIN_EMAIL";
    static final String PASSWORD_VARIABLE = "BOOTSTRAP_ADMIN_PASSWORD";

    private static final Logger logger = LoggerFactory.getLogger(BootstrapFirstAdminUseCase.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapFirstAdminUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return true when the administrator was created, false when nothing was done
     * @throws IllegalStateException when the users table is empty and the variables are half set or invalid
     */
    public boolean execute(String email, String password) {
        if (userRepository.count() > 0) {
            return false;
        }
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isEmpty();
        if (!hasEmail && !hasPassword) {
            logger.warn("There are no users and {} / {} are not set: nobody can sign in. "
                + "Set both for the first start to create the first administrator.", EMAIL_VARIABLE, PASSWORD_VARIABLE);
            return false;
        }
        if (!hasEmail || !hasPassword) {
            throw new IllegalStateException(
                (hasEmail ? PASSWORD_VARIABLE : EMAIL_VARIABLE) + " is not set: the first administrator needs both "
                    + EMAIL_VARIABLE + " and " + PASSWORD_VARIABLE);
        }

        Email validEmail = validEmail(email);
        validatePassword(password);

        User admin = new User(validEmail, passwordEncoder.encode(password), UserRole.ADMIN);
        // Whole seconds, as ChangePasswordUseCase stores it: token issue times are compared with this value.
        admin.setLastPasswordChange(admin.getLastPasswordChange().truncatedTo(ChronoUnit.SECONDS));
        userRepository.save(admin);
        logger.info("Created the first administrator {}", validEmail.getValue());
        return true;
    }

    private static Email validEmail(String email) {
        try {
            return Email.of(email);
        } catch (UserDomainException e) {
            throw new IllegalStateException(EMAIL_VARIABLE + " is not a valid email address");
        }
    }

    private static void validatePassword(String password) {
        try {
            User.validatePasswordStrength(password);
        } catch (UserDomainException e) {
            throw new IllegalStateException(PASSWORD_VARIABLE + " is not strong enough: " + e.getMessage());
        }
    }
}
