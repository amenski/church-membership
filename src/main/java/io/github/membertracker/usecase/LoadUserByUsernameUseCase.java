package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;

public class LoadUserByUsernameUseCase {

    private final UserRepository userRepository;

    public LoadUserByUsernameUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User invoke(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserDomainException.userNotFound(email));

        if (!user.isEnabled()) {
            throw UserDomainException.userAlreadyDisabled(email);
        }

        if (user.isAccountLocked()) {
            throw UserDomainException.accountLocked(email);
        }

        if (!user.isCredentialsNonExpired()) {
            throw UserDomainException.credentialsExpired(email);
        }

        return user;
    }
}