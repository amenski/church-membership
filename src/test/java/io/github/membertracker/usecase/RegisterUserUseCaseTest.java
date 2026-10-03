package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {

    private static final String STRONG = "Passw0rd!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new RegisterUserUseCase(userRepository, passwordEncoder);
        when(passwordEncoder.encode(STRONG)).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void registersWithEncodedPasswordNormalisedEmailAndGivenRole() {
        User result = useCase.invoke("New.User@Example.COM", STRONG, UserRole.STAFF);

        User saved = savedUser();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getUsername()).isEqualTo("new.user@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void defaultsToMemberRoleWhenRoleOmittedOrNull() {
        useCase.invoke("a@example.com", STRONG);
        assertThat(savedUser().getRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void nullRoleFallsBackToMember() {
        useCase.invoke("a@example.com", STRONG, (UserRole) null);
        assertThat(savedUser().getRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void namesAreTrimmedAndBlankNamesAreIgnored() {
        useCase.invoke("a@example.com", STRONG, UserRole.MEMBER, "  Ada ", "   ");

        User saved = savedUser();
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isNull();
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnythingIsEncodedOrSaved() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke("dup@example.com", STRONG))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.EMAIL_ALREADY_EXISTS));
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void weakPasswordIsRejected() {
        assertThatThrownBy(() -> useCase.invoke("a@example.com", "password"))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.WEAK_PASSWORD));
        verify(userRepository, never()).save(any());
    }

    @Test
    void malformedEmailIsRejected() {
        assertThatThrownBy(() -> useCase.invoke("not-an-email", STRONG))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.INVALID_USER_DATA));
        verify(userRepository, never()).save(any());
    }
}
