package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticateUserUseCaseTest {

    private static final String EMAIL = "user@example.com";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticateUserUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new AuthenticateUserUseCase(userRepository, passwordEncoder, CLOCK);
        user = new User(Email.of(EMAIL), "hash", UserRole.MEMBER);
        user.setId(7L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(passwordEncoder.encode("dummy-password")).thenReturn("dummy-hash");
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(UserDomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private void lockUntil(LocalDateTime until) {
        user.setAccountNonLocked(false);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(until);
    }

    @Test
    void correctPasswordReturnsUserAndResetsTheCounterWithoutSavingTheUser() {
        user.setFailedLoginAttempts(3);

        User result = useCase.invoke(EMAIL, "right");

        assertThat(result).isSameAs(user);
        assertThat(result.getFailedLoginAttempts()).isZero();
        verify(userRepository).resetFailedLogins(7L);
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    void wrongPasswordRecordsTheFailureAtomicallyAndThrows() {
        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_CREDENTIALS);

        verify(userRepository).recordFailedLogin(7L, 5, NOW.plusMinutes(15));
        verify(userRepository, never()).save(any());
    }

    @Test
    void lockedAccountNeverChecksThePasswordAndLooksLikeAnyOtherFailure() {
        lockUntil(NOW.plusMinutes(10));

        UserDomainException locked = catchThrowableOfType(UserDomainException.class, () -> useCase.invoke(EMAIL, "right"));

        assertThat(locked.getErrorCode()).isEqualTo(UserDomainException.INVALID_CREDENTIALS);
        assertThat(locked.getMessage()).isEqualTo(UserDomainException.invalidCredentials().getMessage());
        verify(passwordEncoder, never()).matches("right", "hash");
        verify(passwordEncoder).matches("right", "dummy-hash");
        verify(userRepository, never()).recordFailedLogin(anyLong(), anyInt(), any());
        verify(userRepository, never()).resetFailedLogins(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void permanentLockStaysLockedEvenForTheRightPassword() {
        lockUntil(null);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.INVALID_CREDENTIALS);

        verify(passwordEncoder, never()).matches("right", "hash");
        verify(userRepository, never()).resetFailedLogins(anyLong());
    }

    @Test
    void expiredLockIsClearedAndTheLoginWorks() {
        lockUntil(NOW.minusSeconds(1));

        User result = useCase.invoke(EMAIL, "right");

        assertThat(result.getFailedLoginAttempts()).isZero();
        assertThat(result.isAccountNonLocked()).isTrue();
        verify(userRepository, times(2)).resetFailedLogins(7L); // once for the expired lock, once after success
        verify(userRepository, never()).save(any());
    }

    @Test
    void expiredLockWithAWrongPasswordStartsCountingAgainFromZero() {
        lockUntil(NOW.minusSeconds(1));

        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_CREDENTIALS);

        verify(userRepository).resetFailedLogins(7L);
        verify(userRepository).recordFailedLogin(7L, 5, NOW.plusMinutes(15));
    }

    @Test
    void unknownEmailThrowsInvalidCredentialsRunsDummyCheckAndWritesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        expectCode(() -> useCase.invoke("nobody@example.com", "right"), UserDomainException.INVALID_CREDENTIALS);

        verify(passwordEncoder).matches("right", "dummy-hash");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).recordFailedLogin(anyLong(), anyInt(), any());
    }

    @Test
    void dummyHashIsComputedOnlyOnce() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        expectCode(() -> useCase.invoke("nobody@example.com", "a"), UserDomainException.INVALID_CREDENTIALS);
        expectCode(() -> useCase.invoke("nobody@example.com", "b"), UserDomainException.INVALID_CREDENTIALS);

        verify(passwordEncoder, times(1)).encode("dummy-password");
    }

    @Test
    void unknownEmailWrongPasswordAndLockedAccountGiveTheSameCodeAndMessage() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        User lockedUser = new User(Email.of("locked@example.com"), "hash", UserRole.MEMBER);
        lockedUser.setId(8L);
        lockedUser.setAccountNonLocked(false);
        lockedUser.setLockedUntil(NOW.plusMinutes(5));
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(lockedUser));

        UserDomainException unknown = catchThrowableOfType(UserDomainException.class,
                () -> useCase.invoke("nobody@example.com", "wrong"));
        UserDomainException wrong = catchThrowableOfType(UserDomainException.class,
                () -> useCase.invoke(EMAIL, "wrong"));
        UserDomainException locked = catchThrowableOfType(UserDomainException.class,
                () -> useCase.invoke("locked@example.com", "right"));

        assertThat(unknown.getErrorCode()).isEqualTo(wrong.getErrorCode()).isEqualTo(locked.getErrorCode());
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage()).isEqualTo(locked.getMessage())
                .isEqualTo("Invalid email or password. After several failed attempts an account is locked for 15 minutes.");
    }

    @Test
    void disabledAccountIsRejectedAfterTheRightPassword() {
        user.setEnabled(false);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.USER_ALREADY_DISABLED);

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).resetFailedLogins(eq(7L));
    }

    @Test
    void expiredCredentialsAreRejected() {
        user.setCredentialsNonExpired(false);

        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.CREDENTIALS_EXPIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void theLoginPathNeverSavesTheWholeUser() {
        // A save after the slow BCrypt check could overwrite a password change made in between.
        useCase.invoke(EMAIL, "right");
        expectCode(() -> useCase.invoke(EMAIL, "wrong"), UserDomainException.INVALID_CREDENTIALS);
        lockUntil(NOW.plusMinutes(1));
        expectCode(() -> useCase.invoke(EMAIL, "right"), UserDomainException.INVALID_CREDENTIALS);

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).update(any());
    }
}
