package io.github.membertracker.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapFirstAdminUseCaseTest {

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private BootstrapFirstAdminUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger useCaseLogger;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        // Cost 4 keeps the test fast; the real bean is BCrypt with cost 12 (SecurityConfig).
        passwordEncoder = new BCryptPasswordEncoder(4);
        useCase = new BootstrapFirstAdminUseCase(userRepository, passwordEncoder);
        useCaseLogger = (Logger) LoggerFactory.getLogger(BootstrapFirstAdminUseCase.class);
        logs = new ListAppender<>();
        logs.start();
        useCaseLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        useCaseLogger.detachAppender(logs);
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsAnEnabledAdminWhenThereAreNoUsers() {
        when(userRepository.count()).thenReturn(0L);

        boolean created = useCase.execute("owner@example.org", PASSWORD);

        assertThat(created).isTrue();
        User admin = savedUser();
        assertThat(admin.getEmailValue()).isEqualTo("owner@example.org");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isAccountNonLocked()).isTrue();
        assertThat(admin.isCredentialsNonExpired()).isTrue();
        assertThat(admin.getFailedLoginAttempts()).isZero();
        assertThat(admin.getLastPasswordChange().getNano()).isZero();
    }

    @Test
    void storesTheBcryptHashNotThePlainPassword() {
        when(userRepository.count()).thenReturn(0L);

        useCase.execute("owner@example.org", PASSWORD);

        String stored = savedUser().getPassword();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
    }

    @Test
    void normalisesTheEmailLikeEveryOtherAccount() {
        when(userRepository.count()).thenReturn(0L);

        useCase.execute("  Owner@Example.ORG ", PASSWORD);

        assertThat(savedUser().getEmailValue()).isEqualTo("owner@example.org");
    }

    @Test
    void logsOneInfoLineWithTheEmailAndNeverThePassword() {
        when(userRepository.count()).thenReturn(0L);

        useCase.execute("owner@example.org", PASSWORD);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(logs.list.get(0).getFormattedMessage()).isEqualTo("Created the first administrator owner@example.org");
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(PASSWORD));
    }

    @Test
    void doesNothingWhenAUserAlreadyExists() {
        when(userRepository.count()).thenReturn(1L);

        boolean created = useCase.execute("owner@example.org", PASSWORD);

        assertThat(created).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenAUserExistsEvenIfTheValuesAreInvalid() {
        when(userRepository.count()).thenReturn(3L);

        assertThat(useCase.execute("not-an-email", "weak")).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenTheVariablesAreNotSet() {
        when(userRepository.count()).thenReturn(0L);

        assertThat(useCase.execute("", "")).isFalse();
        assertThat(useCase.execute(null, null)).isFalse();
        assertThat(useCase.execute("  ", "")).isFalse();

        verify(userRepository, never()).save(any());
    }

    @Test
    void warnsThatNobodyCanSignInWhenThereAreNoUsersAndNoVariables() {
        when(userRepository.count()).thenReturn(0L);

        useCase.execute("", "");

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage())
            .contains("BOOTSTRAP_ADMIN_EMAIL").contains("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void stopsTheStartWhenOnlyTheEmailIsSet() {
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> useCase.execute("owner@example.org", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD is not set");
        verify(userRepository, never()).save(any());
    }

    @Test
    void stopsTheStartWhenOnlyThePasswordIsSet() {
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> useCase.execute("", PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL is not set")
            .hasMessageNotContaining(PASSWORD);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAWeakPasswordNamingTheVariableWithoutEchoingIt() {
        when(userRepository.count()).thenReturn(0L);
        String weak = "weakpass1";

        assertThatThrownBy(() -> useCase.execute("owner@example.org", weak))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
            .hasMessageContaining("8 to 72 characters")
            .hasMessageNotContaining(weak)
            .satisfies(e -> assertThat(e.getCause()).isNull());
        verify(userRepository, never()).save(any());
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(weak));
    }

    @Test
    void rejectsAPasswordLongerThan72Bytes() {
        when(userRepository.count()).thenReturn(0L);
        String tooLong = "Aa1!" + "x".repeat(70);

        assertThatThrownBy(() -> useCase.execute("owner@example.org", tooLong))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
            .hasMessageNotContaining(tooLong);
    }

    @Test
    void rejectsABadEmailNamingTheVariableWithoutEchoingThePassword() {
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> useCase.execute("not-an-email", PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
            .hasMessageNotContaining(PASSWORD);
        verify(userRepository, never()).save(any());
    }

    @Test
    void neverHashesOrSavesWhenTheInputIsInvalid() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserRepository repository = mock(UserRepository.class);
        when(repository.count()).thenReturn(0L);
        BootstrapFirstAdminUseCase strict = new BootstrapFirstAdminUseCase(repository, encoder);

        assertThatThrownBy(() -> strict.execute("owner@example.org", "weak")).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(encoder);
        verify(repository, never()).save(any());
    }
}
