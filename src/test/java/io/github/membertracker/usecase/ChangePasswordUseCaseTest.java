package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangePasswordUseCaseTest {

    private static final String BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BUwYhXKZl1u2bQ3vGZ0H8s6y9Ety";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private ChangePasswordUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new ChangePasswordUseCase(userRepository, passwordEncoder);
        user = new User(Email.of("user@example.com"), "oldHash", UserRole.MEMBER);
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1!", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn(BCRYPT_HASH);
    }

    @Test
    void storesTheEncodedNewPasswordNotThePlainOneWithATargetedUpdate() {
        user.setFailedLoginAttempts(2);

        User result = useCase.execute(1L, "OldPass1!", "NewPass1!");

        assertThat(result.getPassword()).isEqualTo(BCRYPT_HASH);
        assertThat(result.getFailedLoginAttempts()).isZero();
        assertThat(result.getLastPasswordChange().getNano()).isZero();
        verify(userRepository).updatePassword(1L, BCRYPT_HASH, result.getLastPasswordChange());
        verify(userRepository, never()).save(any());
    }

    @Test
    void wrongCurrentPasswordCountsTowardTheLockAndNothingChanges() {
        when(passwordEncoder.matches("bad", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(1L, "bad", "NewPass1!"))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.INVALID_PASSWORD))
                .hasMessage("Current password is incorrect");

        assertThat(user.getPassword()).isEqualTo("oldHash");
        verify(userRepository).recordFailedLogin(eq(1L), eq(5), any(LocalDateTime.class));
        verify(userRepository, never()).updatePassword(anyLong(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void lockedAccountIsRejectedWithoutCheckingThePassword() {
        user.setAccountNonLocked(false);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        assertThatThrownBy(() -> useCase.execute(1L, "OldPass1!", "NewPass1!"))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.INVALID_PASSWORD));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).recordFailedLogin(anyLong(), anyInt(), any());
        verify(userRepository, never()).updatePassword(anyLong(), any(), any());
    }

    @Test
    void expiredLockIsClearedFirst() {
        user.setAccountNonLocked(false);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));

        useCase.execute(1L, "OldPass1!", "NewPass1!");

        verify(userRepository).resetFailedLogins(1L);
        verify(userRepository).updatePassword(eq(1L), eq(BCRYPT_HASH), any(LocalDateTime.class));
    }

    @Test
    void weakNewPasswordIsRejectedAndNothingChanges() {
        assertThatThrownBy(() -> useCase.execute(1L, "OldPass1!", "weak"))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.WEAK_PASSWORD));

        assertThat(user.getPassword()).isEqualTo("oldHash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).updatePassword(anyLong(), any(), any());
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(2L, "OldPass1!", "NewPass1!"))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.USER_NOT_FOUND));
        verify(userRepository, never()).updatePassword(anyLong(), any(), any());
    }
}
