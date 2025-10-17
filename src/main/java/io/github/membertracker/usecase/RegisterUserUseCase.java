package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.springframework.security.crypto.password.PasswordEncoder;

public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User invoke(String email, String password, UserRole role) {
        return invoke(email, password, role, null, null);
    }

    public User invoke(String email, String password) {
        return invoke(email, password, UserRole.USER, null, null);
    }

    public User invoke(String email, String password, String firstName, String lastName) {
        return invoke(email, password, UserRole.USER, firstName, lastName);
    }

    public User invoke(String email, String password, UserRole role, String firstName, String lastName) {
        validateUniqueEmail(email);

        User.validatePasswordStrength(password);
        Email emailValue = Email.of(email);
        UserRole userRole = role != null ? role : UserRole.USER;

        User user = new User(emailValue, passwordEncoder.encode(password), userRole);
        if (firstName != null && !firstName.trim().isEmpty()) {
            user.setFirstName(firstName.trim());
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            user.setLastName(lastName.trim());
        }
        return userRepository.save(user);
    }

    private void validateUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw UserDomainException.emailAlreadyExists(email);
        }
    }
}