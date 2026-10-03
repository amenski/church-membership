package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.persistence.entity.UserEntity;
import io.github.membertracker.infrastructure.persistence.repository.UserJpaRepository;
import io.github.membertracker.usecase.AuthenticateUserUseCase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Lockout, auto-unlock and atomic counters through the real stack on in-memory H2. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:lockout;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class LoginLockoutIntegrationTest {

    private static final String EMAIL = "lockout@example.com";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthenticateUserUseCase authenticateUserUseCase;

    private String xsrf;
    private Long userId;

    @BeforeEach
    void createUser() throws Exception {
        MockHttpServletResponse first = mockMvc.perform(get("/api/users/me")).andReturn().getResponse();
        xsrf = first.getCookie("XSRF-TOKEN").getValue();
        userId = userRepository.save(new User(Email.of(EMAIL), passwordEncoder.encode(PASSWORD), UserRole.MEMBER)).getId();
    }

    @AfterEach
    void deleteUser() {
        userJpaRepository.findByEmail(EMAIL).ifPresent(userJpaRepository::delete);
    }

    private MockHttpServletResponse login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .cookie(new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
            .andReturn().getResponse();
    }

    private UserEntity row() {
        return userJpaRepository.findById(userId).orElseThrow();
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndTheRightPasswordStaysRejectedUntilTheLockExpires() throws Exception {
        String generic = UserDomainException.invalidCredentials().getMessage();
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse wrong = login("Wrong1234!");
            assertThat(wrong.getStatus()).isEqualTo(400);
            assertThat(wrong.getContentAsString()).contains(generic);
        }
        UserEntity locked = row();
        assertThat(locked.isAccountNonLocked()).isFalse();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));

        // the sixth attempt, with the RIGHT password, gives the same generic answer
        MockHttpServletResponse sixth = login(PASSWORD);
        assertThat(sixth.getStatus()).isEqualTo(400);
        assertThat(sixth.getContentAsString()).contains(generic).doesNotContainIgnoringCase("locked for user");
        assertThat(sixth.getCookie("sid")).isNull();
        assertThat(row().getFailedLoginAttempts()).isEqualTo(5); // the locked attempt is not counted or extended

        // time passes: the lock ends by itself
        UserEntity entity = row();
        entity.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userJpaRepository.save(entity);

        MockHttpServletResponse ok = login(PASSWORD);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getCookie("sid")).isNotNull();
        UserEntity after = row();
        assertThat(after.isAccountNonLocked()).isTrue();
        assertThat(after.getFailedLoginAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void parallelFailedAttemptsAreCountedExactly() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    userRepository.recordFailedLogin(userId, 1000, LocalDateTime.now().plusMinutes(15));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        UserEntity result = row();
        assertThat(result.getFailedLoginAttempts()).isEqualTo(20);
        assertThat(result.isAccountNonLocked()).isTrue();
    }

    @Test
    void aLoginCannotUndoAConcurrentPasswordChange() {
        // The login path loads the user, then (in the old code) saved the whole row after the slow hash check.
        // Simulate the change landing in between: the targeted reset must leave the new password alone.
        User stale = userRepository.findById(userId).orElseThrow();
        userRepository.updatePassword(userId, passwordEncoder.encode("NewPassw0rd!"), LocalDateTime.now());

        userRepository.resetFailedLogins(userId);

        assertThat(passwordEncoder.matches("NewPassw0rd!", row().getPassword())).isTrue();
        assertThat(stale.getPassword()).isNotEqualTo(row().getPassword());
    }

    @Test
    void successfulLoginWritesOnlyTheCounterColumns() {
        UserEntity before = row();
        authenticateUserUseCase.invoke(EMAIL, PASSWORD);
        UserEntity after = row();

        assertThat(after.getPassword()).isEqualTo(before.getPassword());
        assertThat(after.isEnabled()).isEqualTo(before.isEnabled());
    }
}
