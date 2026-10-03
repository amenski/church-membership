package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private String dummyHash;

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User invoke(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Same BCrypt cost as for a real account, so timing does not reveal whether the email exists.
            passwordEncoder.matches(password, dummyHash());
            throw UserDomainException.invalidCredentials();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.recordFailedLoginAttempt();
            userRepository.save(user);
            throw UserDomainException.invalidCredentials();
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

    private synchronized String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password");
        }
        return dummyHash;
    }
}