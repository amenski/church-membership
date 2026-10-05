package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;

/**
 * Saves the UI language on the account.
 *
 * Its own use case, and its own endpoint, because the profile PUT reads an absent field as "clear
 * this": sending only a language through it would wipe the phone and the bio.
 */
public class UpdateUserLanguageUseCase {

    private final UserRepository userRepository;

    public UpdateUserLanguageUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The saved user; a language the UI does not ship leaves the stored one alone. */
    public User execute(Long userId, String language) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserDomainException.userNotFound(userId.toString()));

        if (!user.changeLanguage(language)) {
            return user;
        }
        return userRepository.update(user);
    }
}
