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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadUserByUsernameUseCaseTest {

    private static final String EMAIL = "user@example.com";

    private UserRepository userRepository;
    private LoadUserByUsernameUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new LoadUserByUsernameUseCase(userRepository);
        user = new User(Email.of(EMAIL), "hash", UserRole.STAFF);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void expectCode(String code) {
        assertThatThrownBy(() -> useCase.invoke(EMAIL)).isInstanceOfSatisfying(UserDomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void returnsHealthyUser() {
        assertThat(useCase.invoke(EMAIL)).isSameAs(user);
    }

    @Test
    void unknownEmailThrowsNotFound() {
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.invoke("x@example.com")).isInstanceOfSatisfying(UserDomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.USER_NOT_FOUND));
    }

    @Test
    void disabledUserIsRejected() {
        user.setEnabled(false);
        expectCode(UserDomainException.USER_ALREADY_DISABLED);
    }

    @Test
    void lockedUserIsRejected() {
        user.setAccountNonLocked(false);
        expectCode(UserDomainException.ACCOUNT_LOCKED);
    }

    @Test
    void expiredCredentialsAreRejected() {
        user.setCredentialsNonExpired(false);
        expectCode(UserDomainException.CREDENTIALS_EXPIRED);
    }
}
