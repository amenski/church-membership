package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.infrastructure.security.LoginAttemptLimiter;
import io.github.membertracker.usecase.AuthenticateUserUseCase;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.utils.CookieUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AuthProperties.class, CookieUtils.class, LoginAttemptLimiter.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticateUserUseCase authenticateUserUseCase;
    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;

    private MockHttpServletResponse login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"whatever\"}"))
            .andReturn().getResponse();
    }

    private static String field(MockHttpServletResponse response, String name) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$." + name);
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        // the use case throws the same exception for both cases
        when(authenticateUserUseCase.invoke(anyString(), anyString()))
            .thenThrow(UserDomainException.invalidCredentials());

        MockHttpServletResponse unknown = login("nobody@example.com");
        MockHttpServletResponse wrong = login("known@example.com");

        assertThat(unknown.getStatus()).isEqualTo(400).isEqualTo(wrong.getStatus());
        assertThat(field(unknown, "detail")).isEqualTo(UserDomainException.invalidCredentials().getMessage()).isEqualTo(field(wrong, "detail"));
        assertThat(field(unknown, "title")).isEqualTo(field(wrong, "title"));
    }

    @Test
    void unexpectedExceptionIsNotLeakedToTheClient() throws Exception {
        when(authenticateUserUseCase.invoke(eq("a@example.com"), anyString()))
            .thenThrow(new RuntimeException("secret-db-detail"));

        MockHttpServletResponse response = login("a@example.com");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).doesNotContain("secret-db-detail");
    }

    @Test
    void eleventhFailedAttemptForOneEmailIsThrottledWithRetryAfter() throws Exception {
        when(authenticateUserUseCase.invoke(anyString(), anyString()))
            .thenThrow(UserDomainException.invalidCredentials());

        for (int i = 0; i < 10; i++) {
            assertThat(login("victim@example.com").getStatus()).isEqualTo(400);
        }
        MockHttpServletResponse throttled = login("victim@example.com");

        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(throttled.getHeader("Retry-After"))).isBetween(1L, 600L);
        assertThat(field(throttled, "detail")).isEqualTo("Too many sign-in attempts. Try again in a few minutes.");
        // another email is unaffected (it only counts toward the IP limit)
        assertThat(login("other@example.com").getStatus()).isEqualTo(400);
    }

    @Test
    void successfulSignInsAreNeverThrottled() throws Exception {
        User ok = new User(Email.of("good@example.com"), "hash", UserRole.MEMBER);
        when(authenticateUserUseCase.invoke(eq("good@example.com"), anyString())).thenReturn(ok);

        for (int i = 0; i < 40; i++) {
            assertThat(login("good@example.com").getStatus()).isEqualTo(200);
        }
    }
}
