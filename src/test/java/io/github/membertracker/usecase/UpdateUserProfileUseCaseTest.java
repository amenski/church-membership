package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserProfileUseCaseTest {

    private UserRepository userRepository;
    private UpdateUserProfileUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new UpdateUserProfileUseCase(userRepository);
        user = new User(Email.of("user@example.com"), "hash", UserRole.MEMBER);
        user.setId(1L);
        user.setFirstName("Old");
        user.setLastName("Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void trimsAndPersistsProfileViaUpdate() {
        User result = useCase.execute(1L, " Ada ", " Lovelace ", " +123 ", " bio ");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(saved.capture());
        assertThat(saved.getValue()).isSameAs(result);
        assertThat(result.getFirstName()).isEqualTo("Ada");
        assertThat(result.getLastName()).isEqualTo("Lovelace");
        assertThat(result.getPhone()).isEqualTo("+123");
        assertThat(result.getBio()).isEqualTo("bio");
        verify(userRepository, never()).save(any());
    }

    @Test
    void blankNamesKeepExistingOnesButPhoneAndBioAreOverwritten() {
        user.setPhone("old");
        user.setBio("old bio");

        useCase.execute(1L, " ", null, null, null);

        assertThat(user.getFirstName()).isEqualTo("Old");
        assertThat(user.getLastName()).isEqualTo("Name");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getBio()).isNull();
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(2L, "a", "b", null, null))
                .isInstanceOfSatisfying(UserDomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserDomainException.USER_NOT_FOUND));
        verify(userRepository, never()).update(any());
    }
}
