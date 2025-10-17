package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User invoke(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserDomainException.userNotFound(email));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.recordFailedLoginAttempt();
            userRepository.save(user);
            throw UserDomainException.invalidPassword();
        }

        if (!user.isEnabled()) {
            throw UserDomainException.userAlreadyDisabled(email);
        }

        if (user.isAccountLocked()) {
            throw UserDomainException.accountLocked(email);
        }

        if (!user.isCredentialsNonExpired()) {
            throw UserDomainException.credentialsExpired(email);
        }

        user.resetFailedLoginAttempts();
        userRepository.save(user);
        
        return user;
    }
}