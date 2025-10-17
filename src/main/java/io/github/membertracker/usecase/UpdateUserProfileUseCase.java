package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;

public class UpdateUserProfileUseCase {

    private final UserRepository userRepository;

    public UpdateUserProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User execute(Long userId, String firstName, String lastName, String phone, String bio) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserDomainException.userNotFound(userId.toString()));

        user.updateProfile(firstName, lastName, phone, bio);

        return userRepository.update(user);
    }
}
