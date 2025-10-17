package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.infrastructure.dto.UserResponseDto;

public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    public GetCurrentUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponseDto execute(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserDomainException.userNotFound(email));

        return new UserResponseDto(
                user.getId(),
                user.getEmailValue(),
                user.isEnabled(),
                user.getRole().name(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getBio()
        );
    }
}
