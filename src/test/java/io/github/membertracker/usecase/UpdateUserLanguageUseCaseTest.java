package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserLanguageUseCaseTest {

    private UserRepository userRepository;
    private UpdateUserLanguageUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new UpdateUserLanguageUseCase(userRepository);
        user = new User(Email.of("user@example.com"), "hash", UserRole.MEMBER);
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void savesTheChosenLanguage() {
        User result = useCase.execute(1L, "en");

        assertThat(result.getLanguage()).isEqualTo("en");
        verify(userRepository).update(user);
    }

    @Test
    void anUnsupportedCodeIsLeftOutAndNothingIsWritten() {
        User result = useCase.execute(1L, "fr");

        assertThat(result.getLanguage()).isEqualTo("am");
        verify(userRepository, never()).update(any());
    }

    @Test
    void choosingTheLanguageAlreadyStoredWritesNothing() {
        User result = useCase.execute(1L, "am");

        assertThat(result.getLanguage()).isEqualTo("am");
        verify(userRepository, never()).update(any());
    }

    @Test
    void anUnknownUserIsADomainFailure() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(2L, "en"))
            .isInstanceOf(UserDomainException.class);
    }
}
