package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticateUserUseCaseTest {

    private static final String EMAIL = "user@example.com";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticateUserUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new AuthenticateUserUseCase(userRepository, passwordEncoder);
        user = new User(Email.of(EMAIL), "hash", UserRole.MEMBER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(UserDomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void correctPasswordReturnsUserAndSavesWithResetCounter() {
        user.setFailedLoginAttempts(3);

        User result = useCase.invoke(EMAIL, "right");

        assertThat(result).isSameAs(user);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginAttempts()).isZero();
    }

    @Test
    void wrongPasswordIncrementsCounterSavesAndThrows() {
        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_PASSWORD);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void fifthWrongPasswordLocksTheAccount() {
        user.setFailedLoginAttempts(4);

        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_PASSWORD);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.isAccountLocked()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void fourWrongPasswordsDoNotLockTheAccount() {
        for (int i = 0; i < 4; i++) {
            expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_PASSWORD);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.isAccountLocked()).isFalse();
    }

    @Test
    void lockedAccountWithCorrectPasswordIsRejectedAndNotSaved() {
        user.setAccountNonLocked(false);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.ACCOUNT_LOCKED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void successfulLoginClearsFailedAttemptsBelowLockThreshold() {
        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_PASSWORD);
        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_PASSWORD);

        useCase.invoke(EMAIL, "right");

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void unknownEmailThrowsUserNotFoundAndSavesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        expectCode(() -> useCase.invoke("nobody@example.com", "right"), UserDomainException.USER_NOT_FOUND);

        verify(userRepository, never()).save(any());
    }

    @Test
    void disabledAccountIsRejected() {
        user.setEnabled(false);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.USER_ALREADY_DISABLED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void expiredCredentialsAreRejected() {
        user.setCredentialsNonExpired(false);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.CREDENTIALS_EXPIRED);

        verify(userRepository, never()).save(any());
    }
}
